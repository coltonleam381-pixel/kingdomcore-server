# Manual QA Checklist

## Ability Selection
1. Click the ability NPC with no ability assigned.
2. Verify the ability list shows all 17 abilities.
3. Open a locked ability and verify Agree is hidden.
4. Choose an unclaimed ability and confirm.
5. Verify the menu closes and ability is assigned.
6. Click the ability NPC again and verify upgrade menu opens.

## Ability Activation
1. Rename an item to the assigned ability name.
2. Hold the renamed item and right-click.
3. Verify the ability effect triggers.
4. Spam right-click and verify cooldown and micro cooldown silently block.

## Upgrades
1. Hold heart items in main hand.
2. Open upgrade menu and click Upgrade.
3. Verify heart items are consumed from main hand only.
4. Verify level increments and costs increase.

## Heart Progression
1. Sneak + right-click a heart item.
2. Verify progression increases and max health updates.
3. Die once and verify a heart item drops and progression decreases.
4. Reduce progression to 1, die to a player, and verify immediate kick with blocked state.
5. Reduce progression to 0 and verify login is blocked.

## Revive Flow
1. Click revive NPC.
2. Enter a blocked player's exact nickname in chat.
3. Confirm revive.
4. Verify revive beacon is consumed and target is unblocked with 3 hearts.
5. If target is online, verify teleport to bed spawn or world spawn.
6. Type `cancel` while pending and verify revive input is cleared.
7. Use `/revivecancel` and verify revive input is cleared.

## Crown Bonus
1. Equip crown item in helmet slot.
2. Verify max health increases by +10 hearts.
3. Remove crown and verify max health returns.

## Allowlist
1. Add a nickname with `/allowlist add`.
2. Attempt login with allowed and disallowed names.
3. Verify disallowed names are rejected at pre-login.

## Admin Debug
1. `/kingdomcore debug on`.
2. Trigger a few failed activations.
3. `/kingdomcore debug player <nick>` and verify counters report.

## PvP-Only Death Penalty
1. Die to environment damage.
2. Verify no heart penalty when `hearts.pvp-only-death-penalty` is true.
