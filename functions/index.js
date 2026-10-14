const crypto = require("node:crypto");
const admin = require("firebase-admin");
const logger = require("firebase-functions/logger");
const {
  onDocumentCreatedWithAuthContext,
  onDocumentUpdatedWithAuthContext
} = require("firebase-functions/v2/firestore");
const { preferenceEnabled, hasMeaningfulChange } = require("./notificationPolicy");

admin.initializeApp();
const db = admin.firestore();

Object.assign(exports, require("./roleManagement"));

const MESSAGE = Object.freeze({
  marketplace: {
    preference: "notifyMarketplace",
    title: "New Marketplace Listing",
    body: "A new item has been posted on Santevi Marketplace."
  },
  communityChat: {
    preference: "notifyCommunityChat",
    title: "New Community Chat Message",
    body: "A new message has been posted in Santevi Community Chat."
  },
  concernsRequests: {
    preference: "notifyConcernsRequests",
    title: "Concerns and Requests Update",
    body: "There is a new update on a Santevi resident concern or request."
  },
  announcements: {
    preference: "notifyAnnouncements",
    title: "Santevi Announcement",
    body: "A new Santevi community announcement is available."
  },
  communityEvents: {
    preference: "notifyCommunityEvents",
    title: "Community Event Update",
    body: "The Santevi community events calendar has been updated."
  },
  amenityReservations: {
    preference: "notifyAmenityReservations",
    title: "Upcoming Amenity Reservation",
    body: "A new community amenity reservation has been posted."
  },
  emergencyContactUpdates: {
    preference: "notifyEmergencyContactUpdates",
    title: "Emergency Contact Update",
    body: "A resident updated their emergency contact or address details."
  }
});

function stableDocumentId(eventId) {
  return crypto.createHash("sha256").update(eventId).digest("hex");
}

async function listTrustedAdminUids() {
  const result = [];
  let pageToken;
  do {
    const page = await admin.auth().listUsers(1000, pageToken);
    for (const user of page.users) {
      if (user.customClaims?.admin === true) result.push(user.uid);
    }
    pageToken = page.pageToken;
  } while (pageToken);
  return result;
}

async function recipientProfiles({ preference, excludedUid, onlyUids }) {
  const profiles = [];
  if (onlyUids) {
    const snapshots = await Promise.all(onlyUids.map((uid) => db.collection("users").doc(uid).get()));
    for (const snapshot of snapshots) {
      if (snapshot.exists) profiles.push(snapshot);
    }
  } else {
    const snapshot = await db.collection("users").get();
    profiles.push(...snapshot.docs);
  }

  return profiles.filter((profile) =>
    profile.id !== excludedUid && preferenceEnabled(profile.data(), preference)
  );
}

async function deliverForEvent(event, { category, documentId, actorUid, onlyUids, route }) {
  const messageType = MESSAGE[category];
  const marker = db.collection("notification_dispatches").doc(stableDocumentId(event.id));
  try {
    await marker.create({
      category,
      sourceDocumentId: documentId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      status: "claimed"
    });
  } catch (error) {
    if (error.code === 6 || error.code === "already-exists") return;
    throw error;
  }

  try {
    const profiles = await recipientProfiles({
      preference: messageType.preference,
      excludedUid: actorUid,
      onlyUids
    });
    const devices = [];
    for (const profile of profiles) {
      const tokenSnapshot = await profile.ref.collection("fcmTokens").get();
      for (const tokenDoc of tokenSnapshot.docs) {
        const token = tokenDoc.get("token");
        if (typeof token === "string" && token.length > 0) {
          devices.push({ token, ref: tokenDoc.ref });
        }
      }
    }

    let sent = 0;
    let removed = 0;
    for (let offset = 0; offset < devices.length; offset += 500) {
      const batch = devices.slice(offset, offset + 500);
      const response = await admin.messaging().sendEachForMulticast({
        tokens: batch.map((device) => device.token),
        notification: { title: messageType.title, body: messageType.body },
        data: {
          category,
          documentId,
          route: route || category,
          notificationId: String(parseInt(stableDocumentId(event.id).slice(0, 7), 16) & 0x7fffffff)
        },
        android: { priority: "high", notification: { channelId: "santevi_community_updates" } }
      });
      sent += response.successCount;

      const invalidTokenRefs = [];
      response.responses.forEach((result, index) => {
        const code = result.error?.code;
        if (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token") {
          invalidTokenRefs.push(batch[index].ref.delete());
        }
      });
      await Promise.all(invalidTokenRefs);
      removed += invalidTokenRefs.length;
    }

    await marker.update({
      status: "complete",
      recipientCount: profiles.length,
      deviceCount: devices.length,
      sentCount: sent,
      removedTokenCount: removed,
      completedAt: admin.firestore.FieldValue.serverTimestamp()
    });
  } catch (error) {
    await marker.update({
      status: "failed",
      errorCode: error.code || "unknown",
      completedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    logger.error("Santevi notification dispatch failed", {
      category,
      sourceDocumentId: documentId,
      errorCode: error.code || "unknown"
    });
  }
}

exports.marketplaceListingPublished = onDocumentCreatedWithAuthContext(
  "marketplace_items/{listingId}",
  async (event) => {
    const listing = event.data?.data();
    if (!listing) return;
    await deliverForEvent(event, {
      category: "marketplace",
      documentId: event.params.listingId,
      actorUid: listing.sellerUid || event.authId,
      route: `marketplace/${event.params.listingId}`
    });
  }
);

exports.communityChatMessagePublished = onDocumentCreatedWithAuthContext(
  "community_chat/{postId}",
  async (event) => {
    const post = event.data?.data();
    if (!post) return;
    await deliverForEvent(event, {
      category: "communityChat",
      documentId: event.params.postId,
      actorUid: post.senderUid || event.authId,
      route: "chat"
    });
  }
);

exports.communityChatReplyPublished = onDocumentCreatedWithAuthContext(
  "community_chat/{postId}/replies/{replyId}",
  async (event) => {
    const reply = event.data?.data();
    if (!reply) return;
    const parent = await db.collection("community_chat").doc(event.params.postId).get();
    const postOwnerUid = parent.get("senderUid");
    if (!postOwnerUid) return;
    await deliverForEvent(event, {
      category: "communityChat",
      documentId: event.params.postId,
      actorUid: reply.senderUid || event.authId,
      onlyUids: [postOwnerUid],
      route: "chat"
    });
  }
);

exports.concernCreated = onDocumentCreatedWithAuthContext(
  "homeowner_concerns/{concernId}",
  async (event) => {
    const concern = event.data?.data();
    if (!concern) return;
    const adminUids = await listTrustedAdminUids();
    await deliverForEvent(event, {
      category: "concernsRequests",
      documentId: event.params.concernId,
      actorUid: concern.reporterUid || event.authId,
      onlyUids: adminUids,
      route: "concerns"
    });
  }
);

exports.concernStatusChanged = onDocumentUpdatedWithAuthContext(
  "homeowner_concerns/{concernId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || !hasMeaningfulChange(before, after, ["status"])) return;
    await deliverForEvent(event, {
      category: "concernsRequests",
      documentId: event.params.concernId,
      actorUid: event.authId,
      onlyUids: after.reporterUid ? [after.reporterUid] : [],
      route: "concerns"
    });
  }
);

exports.announcementPublished = onDocumentCreatedWithAuthContext(
  "announcements/{announcementId}",
  async (event) => {
    if (!event.data?.exists) return;
    await deliverForEvent(event, {
      category: "announcements",
      documentId: event.params.announcementId,
      actorUid: event.authId,
      route: "home"
    });
  }
);

exports.communityEventPublished = onDocumentCreatedWithAuthContext(
  "community_events/{eventId}",
  async (event) => {
    if (!event.data?.exists) return;
    await deliverForEvent(event, {
      category: "communityEvents",
      documentId: event.params.eventId,
      actorUid: event.authId,
      route: "home"
    });
  }
);

exports.communityEventUpdated = onDocumentUpdatedWithAuthContext(
  "community_events/{eventId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || !hasMeaningfulChange(before, after, ["eventName", "date", "location", "description"])) return;
    await deliverForEvent(event, {
      category: "communityEvents",
      documentId: event.params.eventId,
      actorUid: event.authId,
      route: "home"
    });
  }
);

exports.amenityReservationPublished = onDocumentCreatedWithAuthContext(
  "amenity_reservations/{reservationId}",
  async (event) => {
    const reservation = event.data?.data();
    if (!reservation) return;
    await deliverForEvent(event, {
      category: "amenityReservations",
      documentId: event.params.reservationId,
      actorUid: reservation.reservedByUid || event.authId,
      route: "home"
    });
  }
);

exports.emergencyContactProfileUpdated = onDocumentUpdatedWithAuthContext(
  "users/{userId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || !hasMeaningfulChange(before, after, ["contact", "block", "lot", "phase"])) return;
    await deliverForEvent(event, {
      category: "emergencyContactUpdates",
      documentId: event.params.userId,
      actorUid: event.params.userId,
      route: "profile"
    });
  }
);
