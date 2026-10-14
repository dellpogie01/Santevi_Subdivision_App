// Run once from a trusted operator environment with Firebase Admin IAM access.
// Never package this script or its credentials in the Android application.
const admin = require("firebase-admin");

async function provisionFirstAdmin() {
  admin.initializeApp({ projectId: process.env.GOOGLE_CLOUD_PROJECT || process.env.GCLOUD_PROJECT || "santevi" });
  const db = admin.firestore();
  const auth = admin.auth();
  const email = "juanmediahubph@gmail.com";
  const user = await auth.getUserByEmail(email);
  if (!user.emailVerified) throw new Error("The target Authentication account must have a verified email.");
  const admins = [];
  let pageToken;
  do {
    const page = await auth.listUsers(1000, pageToken);
    admins.push(...page.users.filter((candidate) => candidate.customClaims?.admin === true));
    pageToken = page.pageToken;
  } while (pageToken);
  if (admins.some((candidate) => candidate.uid !== user.uid)) {
    throw new Error("Another active administrator already exists; first-admin provisioning stopped.");
  }

  const marker = db.collection("systemMetadata").doc("initialAdminProvisioning");
  const existing = await marker.get();
  if (existing.exists) {
    throw new Error("The one-time provisioning marker already exists; inspect it before taking any recovery action.");
  }
  const userRef = db.collection("users").doc(user.uid);
  await db.runTransaction(async (tx) => {
    const state = await tx.get(marker);
    if (state.exists) throw new Error("Provisioning marker already exists.");
    tx.set(marker, { status: "in_progress", targetUid: user.uid, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  });
  await auth.setCustomUserClaims(user.uid, { ...(user.customClaims || {}), admin: true });
  await userRef.set({
    uid: user.uid,
    email: user.email,
    role: "HOA Officer",
    isAdmin: true,
    roleVerified: true,
    roleRequestStatus: "none",
    requestedRole: "",
    accountStatus: "active",
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  await marker.set({ status: "complete", targetUid: user.uid, completedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  console.log(`Provisioned initial administrator UID ${user.uid}.`);
}

provisionFirstAdmin().catch((error) => {
  console.error("Initial administrator provisioning failed:", error.message);
  process.exitCode = 1;
});
