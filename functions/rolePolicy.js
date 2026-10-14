const ORDINARY_ROLES = Object.freeze(["Visitor", "Homeowner"]);
const ELEVATED_ROLES = Object.freeze([
  "Security", "HOA Officer", "Property Manager", "Maintenance",
  "Community Staff", "Service Provider", "Emergency Response"
]);

function isOrdinaryRole(role) { return ORDINARY_ROLES.includes(role); }
function isElevatedRole(role) { return ELEVATED_ROLES.includes(role); }

module.exports = { ORDINARY_ROLES, ELEVATED_ROLES, isOrdinaryRole, isElevatedRole };
