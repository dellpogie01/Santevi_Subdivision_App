## DOMAIN: Design Taste & Anti-AI-Slop (the layer mobile skills skip)

This domain exists because every other domain makes the app *correct*; this one makes it not look
AI-generated. Compose mechanics (state, recomposition, lifecycle, LazyColumn keys, Hilt, test
pyramid, baseline profiles) are already fully covered in Domains 1–5 and are **not** repeated here.
`ui-ux-pro-max`'s `jetpack-compose.csv` was checked and is *weaker* than what this skill already
carries — deliberately not merged. What's new here, and the only reason this domain exists:
(1) the Anthropic design *process* (subject-first, no-decision-is-a-default, plan→review→build→critique,
restraint); (2) a catalog of AI *tells* with severities and fixes, merged and deduped from
`avoid-ai-design`, `kill-ai-slop` (35-tell taxonomy) and `anthropics/frontend-design`; (3) committing
to one aesthetic direction; (4) component-craft rules (corner nesting, borders that die, shadow scale,
status-box discipline) the mobile domains don't have; (5) the native pre-delivery visual checklist that
extends Domain 10. Read before building any screen the user will *look at*, and run §5 before shipping.

### 1. The governing principle (this overrides cleverness)

- **Slop is the absence of a decision, not a banned color.** A tell is a default nobody chose; the
  same element, chosen and defensible, is fine. Never treat a hex, a font, a gradient, or a card as
  illegal in the abstract — ask "did someone *choose* this for this product?" If you can't articulate
  a reason tied to the brief, it's a default; change it or state the reason.
- **A model regresses to the visual median of its training data** (a decade of Tailwind/shadcn/AI
  demos). "Looks designed" isn't in the objective, so absent direction it emits the average — which is
  exactly the purple-glass-Inter-3-card look. The fix is always the same: **replace a default with a
  decision.**
- **Don't swap one default for the next-newest default.** 2024 slop = indigo gradient on dark.
  2026 "tasteful" slop = warm cream + Instrument-Serif/Playfair display + a terracotta/sage accent +
  calm-editorial spacing, applied regardless of product. If the last three screens you made reached
  for cream+serif, that's a tell too. Vary across projects; repetition is the tell.
- **Ground every choice in the subject.** Before styling, name the concrete subject, audience, and the
  page's one job. A tracker for habit-forming, a fintech dashboard, and a kids' app must not share a
  look. Structure is information: numbering, eyebrows, dividers, badges encode something or get cut.
- **Spend boldness in ONE place.** Let a single element be the memorable thing; keep everything around
  it disciplined. Chanel rule: before shipping, remove one accessory. Restraint executed well is a
  decision — reward it; timid uniformity is not.

### 2. AI tells — catalog, deduped, with the fix

Severity: **P0** screams AI on sight (always fix) · **P1** obvious smell (fix every pass) ·
**P2** cosmetic (fix when cheap). 👁 = needs the *rendered* screen to judge honestly (palette weight,
spacing rhythm, hierarchy, motion) — without a render, flag lower-confidence. Percentages are from a
Playwright audit of ~1,400 recent Show-HN sites; read as relative commonness, not gospel.

**Color**
- **C1 P0 indigo→violet / purple→blue diagonal gradient** on hero, CTA, or glow (`#6366f1→#a855f7`;
  ~the Tailwind indigo-500 lineage). → pick one brand-dominant + one sharp accent; if a gradient, make
  it tonal/single-hue with a *reason*, not a "premium" sticker.
- **C2 P0 gradient-clip headline text** (`bg-clip-text text-transparent`). Trades contrast for a
  flourish every AI page uses. → solid ink headings; hierarchy via size/weight/space.
- **C3 P1 indigo/violet as the *unchosen* primary CTA** (~11%). → tie the action to the brand accent;
  give it real hover/active states.
- **C4 P1 default semantic palette** — info=blue, tip=amber, success=green, error=red, straight from the
  framework's `-50`/`-600` ramp: a bowl of unrelated candy. → derive semantics from *your* palette
  (tints of your hues + neutrals); colour only the states that genuinely differ; most rows need none.
- **C5 P1 one-hue status box** — border+text+bg all one hue, bg just a translucent version
  (`border-red-500 text-red-500 bg-red-500/10`); traffic-light, not a palette. → carry state in words
  and weight first; at most one muted accent on a neutral surface.
- **C6 P1 timid evenly-spread palette** 👁 — 3–4 colors, no dominant, no sharp accent; AI spreads color
  and avoids commitment (real brands commit: 60/30/10). → one dominant, one secondary, one accent used
  sparingly. *Deep* palettes carry 8–12 values once tints/states are counted; AI ships 3–4.
- **C7 P1 "premium dark" glow atmosphere** — near-black page with a top spotlight glow + cards whose
  own surface is a vertical gradient + colored box-shadow accents. → one flat background held; depth
  from a hairline + a restrained colorless shadow; if a glow exists, it must *point at something*.
- **C8 P2 colored glow shadow** on a card/button (`shadow-indigo-500/50`). → shadow means elevation,
  not color theater.

**Type**
- **T1 P0 Inter/system-stack/Roboto for everything, no display face, no pairing.** The #1 tell. →
  pair a characterful display (headings) with a clean body face; set both as tokens. For Android the
  analog is: don't ship default `MaterialTheme.typography` untouched as if it were a brand — define a
  custom `Typography` (see Domain 3) and, for a truly distinctive app, a brand face via `Res.font`.
- **T2 P1 the "tasteful free-font" cluster** — Space Grotesk / Geist / Syne / Sora / Instrument Serif /
  Fraunces as the only gesture toward design (the second-order default). → keep one only if it fits a
  named direction and you *pair* it; "never ship the font the last three projects shipped."
- **T3 P1 serif-italic accent word in a sans headline** ("the *modern* way") — Claude's signature, and
  **single-word color/bold highlight** siblings (`kill-ai-slop` 07/13). → earn emphasis with weight/size/
  line-break within one type voice; a real serif/sans contrast is committed *throughout* or not at all;
  at most one accent per paragraph, structure carries the rest.
- **T4 P1 kicker above every heading** — the ALL-CAPS tracked eyebrow (FEATURES / HOW IT WORKS) stamped
  on every section as a reflex, restating what the heading already says. → use sparingly; a kicker must
  *add* a dimension (date, category). Vary section openers: a number, a question, a plain lowercase line,
  or nothing. Let scale + space introduce sections. **Also: ALL-CAPS that survives must carry
  letter-spacing** (unspaced caps hurt reading).
- **T5 P1 full-sentence display headline** (`text-6xl font-extrabold tracking-tight` wrapping to 3–4
  lines, crushing the viewport; tracking negative to look "designed"). → compress the one thing into a
  few big words; say the rest at normal size; tighten tracking only as far as the face was drawn to go.
- **T6 P1 flat type hierarchy** 👁 — everything between 14–18px, hierarchy left to shades of grey
  (`text-lg` heading, `text-sm` everything else); the same absent decision as T5, opposite direction. →
  a scale with real contrast (≥1.25× steps); if two sizes are within ~2px, merge them; give the most
  important thing a size that says so. (Note this is *not* the blanket "bigger fonts" default you may
  have seen — it's "make one thing genuinely dominant", which is the actual principle.)

**Layout & composition**
- **L1 P0 centered hero template** — pill badge + centered H1 + centered subhead + 1–2 centered CTAs
  (~23% center the title; the *combo* is the tell, centering alone is fine). → break symmetry: left/
  oversized-type/split/editorial hero; let one element be dramatically larger; drop the badge unless it
  carries real news.
- **L2 P0 three (or six) identical icon-topped feature cards**, same size/padding, ~20%. The most
  clichéd SaaS pattern; a grid of equal cards is AI laying things out, not deciding. → **default is NO
  card**: use sections/columns/dividers/prose; alternate text-and-visual rows, one large showcase with
  smaller supporting points, and if a grid is genuinely right vary size and density per real hierarchy.
  **Card test: if removing border+shadow+bg+radius costs nothing to interaction or meaning, it isn't a
  card — delete the container.**
- **L3 P1 reflexive bento grid** used because trendy, not because tiles differ in importance → bento only
  when the size *encodes* hierarchy; else pick a layout matching the content's actual structure.
- **L4 P1 generic stat/social-proof strip** ("10k+ users · 99.9% · 4.9★") — placeholder numbers for a
  product with no users. → real numbers or cut it; one true metric beats four invented ones.
- **L5 P2 numbered 1·2·3 "How it works"** — keep only if the process is genuinely ordered; else show the
  product doing the thing.
- **L6 P1 default page shell** — every section `container mx-auto max-w-7xl px-4`; one width forever;
  uniform `gap-6` regardless of content; one section padding top to bottom; 4px-grid spacing with zero
  optical adjustment 👁. → vary container width by section role (narrow editorial vs full-bleed); vary
  section padding by importance; crowd some things, isolate others; whitespace is composition, and
  designers nudge 1–2px for optical alignment — do that on purpose.
- **L7 P2 default four-column footer / three-tier "Most Popular" pricing rings** — build from what the
  site/product actually has.

**Components & craft**
- **K1 P0 untouched shadcn/zinc/slate defaults + default `--radius`** (~24%) — the starter shipped
  unthemed. → change base color, radius, type scale; restyle the primitives you use. (Android analog:
  never ship stock `MaterialTheme` colors/radii unchanged as if it were brand — Domain 3 already
  enforces semantic tokens + shape scale; the *upgrade* is to make those tokens a committed direction,
  not the palette picker's default output.)
- **K2 P1 one radius + one soft shadow on everything** (`rounded-2xl shadow-lg` uniformity) flattens
  hierarchy. → radius and elevation *express* hierarchy: vary radius by role, keep some surfaces flat,
  reserve strong shadows for what truly floats.
- **K3 P1 reflexive glassmorphism / `backdrop-blur`** on nav and cards (real in 2025, now saturated) →
  blurred/translucent surface only where layering is actually real (chrome over scrolling content —
  Domain 10's functional layer); elsewhere solid surfaces with considered color. Blur must indicate
  dismissal, not decorate.
- **K4 P1 colored left/top-border cards** ("colored left borders ≈ em-dashes for AI") applied to every
  list item. → let a list be a list: alignment/spacing/hierarchy; callouts are scarce, one or two a
  page for a real aside.
- **K5 P1 icon-in-a-rounded-square-chip + one per feature**, Lucide `Sparkles`/`Rocket`/`Zap`/
  `ArrowRight` in stock roles (Sparkles+"AI" is the 2024–26 signature) → an icon must carry meaning or
  go; a label + one sentence beats a row of glyphs; pick glyphs for meaning; a real (commissioned or
  refined, *not* a model-sketched blob) icon set or custom marks — Domain 10's SF↔Material map governs
  which glyph means what.
- **K6 P1 badge/pill spam** ("✨ New" "🔥 Popular") → badge only for real status.
- **K7 P1 missing component states** — dead hovers, no transition, forms without
  focus/error/disabled/loading; the happy-path-only polish gap. → build the full state set (Material
  state layers / Compose `enabled`+interaction states); design error + empty states.
- **K8 P1 oversized diffuse shadow / "ghost card"** (hairline border *and* wide soft shadow — two
  separators doing one job). → commit to an edge OR an elevation, not both; a small elevation scale held
  (tight blur, small offset, low opacity, colorless), shadow never bigger than its caster.
- **K9 P1 corners that don't nest** — same large radius on outer and inner box so the arcs fight. →
  inner radius = outer − gap, or don't round the child; keep a small deliberate radius scale.
- **K10 P2 the border that dies at the corner** — 1px hairline stops at the tangent points because the
  radius and the stroke live on different boxes (content in `rounded-xl overflow-hidden`, child carries
  its own border). Right line-by-line, wrong as a whole — the fingerprint of not rendering your output.
  → put radius and border on the *same* box; if lines are really dividers, keep them square edge-to-edge.
- **K11 P2 glowing status dot** (saturated dot + pale halo + pulse in a marketing pill) → small flat
  single-color dot + a word; no halo/pulse; no dot at all if nothing is live behind it.
- **K12 P1 modal scrim legibility** — one opacity reused without measuring → scrim must be judged against
  the real background so foreground stays legible.

**Spacing**
- **S1 P2 uniform padding, no rhythm** 👁 — even whitespace = flat hierarchy. → use the scale to create
  rhythm; sections get distinct vertical space by importance. Softest signal; never lead an audit with it.

**Motion**
- **M1 P2 the same fade-up-on-scroll on every block** (the default AOS reveal) — reflexive, not
  choreographed → pick one or two high-impact moments; a single well-staggered entrance beats a uniform
  reveal; Domain 10's choreography rules carry this on Android.
- **M2 P2 scattered micro-interactions with no motion language** → one motion language: shared duration
  + easing tokens, one orchestrated entrance, exits faster than enters (already in Domain 3/10).
- **M3 P2 the copied "Linear glow"** behind a product shot → borrow the principle (atmosphere, depth),
  not the exact effect; make it specific to *your* brand.
- **M4 P1 emoji as icons/bullets/nav** — inconsistent across platforms, uncontrollable by tokens, reads
  cheap. → real vector icons only (SVG / Material Symbols / custom ImageVector). This is a hard
  no-emoji rule for UI chrome (Domain 10's icon discipline).

**Copy**
- **CP1 P1 vague aspirational headline** ("Build the future of work", "Your all-in-one platform",
  "Scale without limits") that could front any product → write what it actually does, for whom, in
  concrete terms; **specificity is the opposite of slop**.
- **CP2 P2 AI copy voice** — "not just X, it's Y", "Say goodbye to…", punchy three-word triads, the
  em-dash habit, superlatives (best-in-class / cutting-edge / seamless), "may help" hedging → say the
  specific thing; a number, a noun, a consequence.
- **CP3 P1 arrow glyphs glued to labels** ("Get started →", "Learn more →") — the typographic cousin of
  the em-dash → drop it; if a control needs a directional affordance, use a real sized icon, rarely.
- **CP4 active voice, one job, name-by-meaning** — CTA says what happens ("Save", not "Submit";
  "Publish" button → "Published" toast); errors state cause + fix and never apologize; empty screen is
  an invitation to act; name things by what users understand, not how the system is built.

**Imagery**
- **IM1 P1 "diverse team at laptop" stock / generic 3D glossy blobs / DiceBear–pravatar placeholder
  avatars / `aspect-video bg-muted` standing in for a demo** → show the *actual product* (real
  screenshot, real data, real numbers); preserve an image-led reference's *media role* with a real or
  intentional asset; never fake complex imagery with weak CSS blobs.

### 3. Commit to one direction (name the five moves before you build)

Pick **one** direction (or a deliberate blend) for the artifact, then state its defining moves in order:
**type pairing · palette stance · layout stance · motion idea · one signature detail.** Calibrate
intensity to the artifact — a landing can be loud, a settings panel quiet-and-exact. Palette bank below
is a starting point for Android too (map to `ColorScheme`/`Typography`), but the *stance* matters more
than the hexes:
- **Brutalist / raw** (dev tools, indie): one strong grotesque or mono, black-on-off-white or one loud
  primary, hard edges, no radius, borders over shadows, little motion.
- **Editorial / magazine** (content, voice): full serif↔sans contrast (never one italic word), asymmetric
  grid, pull quotes, generous measure — commit fully or not at all.
- **Swiss / International** (data, clarity): one grotesque at many weights, near-monochrome + one signal
  red, strict modular grid, flush-left, the grid *is* the design.
- **Industrial / technical** (B2B, infra): mono or technical grotesque, greyscale + one functional accent,
  dense table-driven, status colors that mean something, near-zero motion, spec-sheet labels.
- **Organic / natural** (wellness, calm): humanist sans or soft serif, muted earth tones, no pure
  white/black, slow eased motion, real texture (grain), organic shapes *specific* to the brand.
- **Playful / toy** (consumer, joy): chunky rounded display, confident color story, overlapping/bouncy
  depth, springy reactive motion, a mascot with a point of view.
- **Luxury / refined**: high-contrast serif + quiet sans, deep restrained palette, wide margins, centered
  as *poise*, slow deliberate reveals, space as the flex.
- (Y2K/synth, art-deco, maximalist, warm-minimal, mono/terminal exist too — reach for warm-minimal only
  with real precision; it is the closest to the AI default.)
Chooser by what the *audience respects*: dev→Brutalist/Industrial; data→Swiss/Industrial; premium→Luxury/
deco; consumer→Playful/Organic; calm SaaS→warm-minimal (only executed precisely).

### 4. Process: plan → review-vs-brief → build → critique (non-negotiable loop)

1. **Plan** a compact token system before code: 4–6 named colors; fonts + their roles; a layout concept
   (one-line prose + rough wireframe, incl. alignment intent); the high-level principle that makes *this*
   screen not-generic.
2. **Review the plan against the brief** — run the same prompt for a generic sibling product in your head;
   if any part reads like the default you'd emit for *any* similar page, revise it and say what you
   changed. Only then build.
3. **Build** to a quality floor without announcing it: responsive to small phone + landscape, keyboard/
   focus-visible, reduced-motion respected, harmonious palette, real component states.
4. **Critique your own output** — if you can render, screenshot and look (a picture beats 1000 tokens);
   check tells visually (C4/C6/S1/T6/M1-M3 need the render). Then re-audit: target **zero P0 tells**.
5. **Self-correct durably** — once told "no gradients on the hero," record it in the project brief so it
   never recurs. Many "looks AI" outcomes are a *specification* problem (unspecified prompt → median),
   not a styling problem: if given no brief, state the choices you made and why instead of silently
   defaulting.

### 5. Pre-delivery taste check (run before "it looks good"; extends Domain 10's checklist)

- [ ] Can I state *why* this color, font, and layout suit **this** product — not just "clean"?
- [ ] Zero **P0** tells: no indigo/purple gradient, no gradient headline, no Inter-everywhere, no
      centered-hero+3-cards combo, no untouched shadcn/Material defaults, no default candy semantic boxes
- [ ] One committed direction named in five moves; boldness spent on one element, rest disciplined
- [ ] **Card test** passed on every card (deleting border+shadow+bg+radius would cost interaction or
      meaning); no badge used as layout; no decorative left-border stripe
- [ ] Type: a real hierarchy (≥1.25× steps, one dominant element); no single-word serif-italic or color
      highlight; no kicker restating its heading; ALL-CAPS carries letter-spacing; no full-sentence display
- [ ] Space has rhythm (varied section padding, container widths by role, optical nudges), not uniform
      `gap-*` everywhere; whitespace groups and separates on purpose
- [ ] Corners nest (inner = outer − gap); a border and its radius share one box (no dead corners); one
      elevation scale, held, colorless; no border+shadow "ghost card"
- [ ] No emoji as icons; one icon family, consistent stroke + fill discipline; `Sparkles`/`Rocket`/
      arrow-glyph-on-CTA retired; icons carry meaning or are gone
- [ ] Motion is choreographed (one orchestrated entrance, exits faster), respects reduced-motion, never
      shifts layout bounds on press
- [ ] Light **and** dark both measured: text ≥4.5:1 both modes, dividers/states distinguishable both,
      scrim judged against real bg, color never the sole signal; dark built on intent not inversion
- [ ] Show the real product (real data/screenshot), no stock-photo/placeholder-avatar/3D-blob filler;
      media role preserved with a real or intentional asset
- [ ] Copy specific and active ("Save changes", "Published"), errors state cause+fix, no em-dash/triad/
      superlative tic; empty states invite action
- [ ] Would pass the "screenshot test" beside a real product's screen — not the current cream-and-serif
      *or* the old purple-gradient look

### 6. What NOT to over-flag (calibration — so you don't become next year's slop)
- One gradient/serif/bento/glass/dark-mode/cream-background used *well and tied to the brief* is a
  choice, not slop. Flag only the reflex default; honor anything the user clearly chose (brand tokens,
  a real logo, a deliberate illustration). Keep an `<!-- keep: chosen -->`-style note on intentional ones.
- Spacing (S1) is soft — never lead an audit with it. Corporate Memphis is a pre-AI lineage, not AI
  evidence. Mesh/aurora/blob backgrounds barely register as real complaints; bento and glass are low
  and *contested* — don't chase them. Restraint done well isn't "timid."
- Over-flagging trains people to ignore you: narrow, evidence-backed, explain the *why*.
