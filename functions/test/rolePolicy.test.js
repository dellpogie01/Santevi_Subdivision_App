const test = require("node:test");
const assert = require("node:assert/strict");
const { ORDINARY_ROLES, ELEVATED_ROLES, isOrdinaryRole, isElevatedRole } = require("../rolePolicy");

test("ordinary roles never include administrator access", () => {
  assert.deepEqual(ORDINARY_ROLES, ["Visitor", "Homeowner"]);
  assert.equal(isOrdinaryRole("Visitor"), true);
  assert.equal(isOrdinaryRole("Homeowner"), true);
  assert.equal(isElevatedRole("Visitor"), false);
});

test("the seven approved community roles are elevated and Administrator is not selectable", () => {
  assert.equal(ELEVATED_ROLES.length, 7);
  for (const role of ELEVATED_ROLES) assert.equal(isElevatedRole(role), true);
  assert.equal(isElevatedRole("Administrator"), false);
  assert.equal(isElevatedRole("HOA"), false);
});
