const LEGACY_DEFAULTS = Object.freeze({
  notifyMarketplace: true,
  notifyCommunityChat: true,
  notifyConcernsRequests: true,
  notifyAnnouncements: false,
  notifyAmenityReservations: false,
  notifyCommunityEvents: false,
  notifyEmergencyContactUpdates: false
});

function preferenceEnabled(profile, field) {
  const value = profile?.[field];
  return typeof value === "boolean" ? value : LEGACY_DEFAULTS[field] === true;
}

function hasMeaningfulChange(before, after, fields) {
  return fields.some((field) => before?.[field] !== after?.[field]);
}

module.exports = { LEGACY_DEFAULTS, preferenceEnabled, hasMeaningfulChange };
