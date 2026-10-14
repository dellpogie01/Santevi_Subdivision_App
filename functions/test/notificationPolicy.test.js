const test = require("node:test");
const assert = require("node:assert/strict");
const { preferenceEnabled, hasMeaningfulChange } = require("../notificationPolicy");

test("legacy notification defaults preserve the three existing toggles", () => {
  assert.equal(preferenceEnabled({}, "notifyMarketplace"), true);
  assert.equal(preferenceEnabled({}, "notifyCommunityChat"), true);
  assert.equal(preferenceEnabled({}, "notifyConcernsRequests"), true);
  assert.equal(preferenceEnabled({}, "notifyAnnouncements"), false);
  assert.equal(preferenceEnabled({}, "notifyAmenityReservations"), false);
  assert.equal(preferenceEnabled({}, "notifyCommunityEvents"), false);
  assert.equal(preferenceEnabled({}, "notifyEmergencyContactUpdates"), false);
});

test("explicit settings override legacy defaults independently", () => {
  const profile = { notifyMarketplace: false, notifyCommunityChat: true };
  assert.equal(preferenceEnabled(profile, "notifyMarketplace"), false);
  assert.equal(preferenceEnabled(profile, "notifyCommunityChat"), true);
});

test("only meaningful field changes produce event notifications", () => {
  assert.equal(hasMeaningfulChange({ status: "Pending" }, { status: "Resolved" }, ["status"]), true);
  assert.equal(hasMeaningfulChange({ status: "Pending" }, { status: "Pending" }, ["status"]), false);
});
