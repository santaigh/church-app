/*
 * Builds docs/church-app-overview.pptx.
 *
 * The first version of this deck was generated and the script thrown away, which meant
 * that when half its "open risks" were solved there was no way to correct it short of
 * rewriting the generator. Hence this file being committed: the deck is an output, and
 * outputs should be reproducible.
 *
 *   cd docs/deck && npm install && npm run build
 *
 * Then re-render the PNGs in docs/slides (PowerPoint via COM -- see render-slides.ps1).
 *
 * One trap worth remembering: pptxgenjs will happily accept a negative width or height
 * and produce a file PowerPoint then refuses to open, with no error from the schema
 * validator. Every dimension below is computed to stay positive.
 */

const PptxGenJS = require("pptxgenjs");
const path = require("path");

// ---------------------------------------------------------------- design tokens

const INK = "3B1018";        // near-black maroon, the ground
const INK_SOFT = "4A1721";   // cards
const ACCENT = "7A1F2B";     // the application's own maroon
const ACCENT_LIGHT = "A8404F";
const TEXT = "F3E9EB";
const MUTED = "C09AA1";
const RULE = "8A3644";

const SERIF = "Georgia";
const SANS = "Calibri";

// LAYOUT_WIDE, not LAYOUT_16x9: both are 16:9, but pptxgenjs makes the latter 10 inches
// across. Every coordinate below assumes 13.333, and with the wrong layout the content
// simply runs off the edge -- which the file itself gives no hint of.
const W = 13.333;
const H = 7.5;
const MARGIN = 0.9;

const pptx = new PptxGenJS();
pptx.layout = "LAYOUT_WIDE";
pptx.author = "Church Management App";
pptx.company = "Parish administration platform";
pptx.subject = "Architecture and design overview";

/** Dark ground plus the two circles the original deck used, kept for continuity. */
function slide({ decorate = true } = {}) {
  const s = pptx.addSlide();
  s.background = { color: INK };
  if (decorate) {
    s.addShape(pptx.ShapeType.ellipse, {
      x: W - 3.2, y: -1.6, w: 4.6, h: 4.6, fill: { color: ACCENT, transparency: 55 },
    });
    s.addShape(pptx.ShapeType.ellipse, {
      x: W - 2.1, y: H - 2.4, w: 3.4, h: 3.4, fill: { color: ACCENT_LIGHT, transparency: 75 },
    });
  }
  return s;
}

function heading(s, eyebrow, title) {
  s.addText(eyebrow.toUpperCase(), {
    x: MARGIN, y: 0.55, w: 9, h: 0.3,
    fontFace: SANS, fontSize: 11, bold: true, color: MUTED, charSpacing: 2,
  });
  s.addText(title, {
    x: MARGIN, y: 0.85, w: 10.5, h: 0.8,
    fontFace: SERIF, fontSize: 34, bold: true, color: TEXT,
  });
}

/** A row of equal cards. Widths are derived, never hand-tuned, so none can go negative. */
function cards(s, items, { y = 2.1, h = 3.4, gap = 0.35 } = {}) {
  const usable = W - MARGIN * 2;
  const w = (usable - gap * (items.length - 1)) / items.length;

  items.forEach((item, i) => {
    const x = MARGIN + i * (w + gap);
    s.addShape(pptx.ShapeType.roundRect, {
      x, y, w, h, rectRadius: 0.08,
      fill: { color: INK_SOFT }, line: { color: RULE, width: 0.75 },
    });
    s.addText(item.title, {
      x: x + 0.28, y: y + 0.25, w: w - 0.56, h: 0.5,
      fontFace: SERIF, fontSize: 17, bold: true, color: TEXT,
    });
    s.addText(item.body, {
      x: x + 0.28, y: y + 0.8, w: w - 0.56, h: h - 1.1,
      fontFace: SANS, fontSize: 12, color: MUTED, lineSpacingMultiple: 1.15, valign: "top",
    });
  });
}

/** Numbered rows, as the original "open risks" slide had. */
function rows(s, items, { y = 1.95, rowH = 1.0, gap = 0.16 } = {}) {
  items.forEach((item, i) => {
    const top = y + i * (rowH + gap);
    s.addShape(pptx.ShapeType.roundRect, {
      x: MARGIN, y: top, w: W - MARGIN * 2, h: rowH, rectRadius: 0.06,
      fill: { color: INK_SOFT }, line: { color: RULE, width: 0.75 },
    });
    s.addText(String(i + 1).padStart(2, "0"), {
      x: MARGIN + 0.25, y: top + 0.22, w: 0.8, h: 0.55,
      fontFace: SERIF, fontSize: 22, bold: true, color: ACCENT_LIGHT,
    });
    s.addText(item.title, {
      x: MARGIN + 1.15, y: top + 0.14, w: 8.6, h: 0.35,
      fontFace: SERIF, fontSize: 15, bold: true, color: TEXT,
    });
    s.addText(item.body, {
      x: MARGIN + 1.15, y: top + 0.48, w: 8.9, h: 0.45,
      fontFace: SANS, fontSize: 11, color: MUTED,
    });
    if (item.tag) {
      s.addText(item.tag, {
        x: W - MARGIN - 1.85, y: top + 0.32, w: 1.6, h: 0.35,
        fontFace: SANS, fontSize: 11, bold: true, color: item.tagColor || MUTED, align: "right",
      });
    }
  });
}

/** Left-to-right flow of boxes with arrows between them. */
function flow(s, steps, { y = 3.0, h = 1.2 } = {}) {
  const usable = W - MARGIN * 2;
  const arrow = 0.45;
  const w = (usable - arrow * (steps.length - 1)) / steps.length;

  steps.forEach((step, i) => {
    const x = MARGIN + i * (w + arrow);
    s.addShape(pptx.ShapeType.roundRect, {
      x, y, w, h, rectRadius: 0.08,
      fill: { color: i === 0 ? ACCENT : INK_SOFT }, line: { color: RULE, width: 0.75 },
    });
    s.addText(step.title, {
      x: x + 0.15, y: y + 0.2, w: w - 0.3, h: 0.35,
      fontFace: SANS, fontSize: 13, bold: true, color: TEXT, align: "center",
    });
    s.addText(step.body, {
      x: x + 0.15, y: y + 0.58, w: w - 0.3, h: 0.5,
      fontFace: SANS, fontSize: 10, color: MUTED, align: "center",
    });
    if (i < steps.length - 1) {
      s.addText("→", {
        x: x + w, y: y + 0.35, w: arrow, h: 0.5,
        fontFace: SANS, fontSize: 18, color: ACCENT_LIGHT, align: "center",
      });
    }
  });
}

function footer(s, text) {
  s.addText(text, {
    x: MARGIN, y: H - 0.72, w: W - MARGIN * 2, h: 0.3,
    fontFace: SANS, fontSize: 10, color: MUTED,
  });
}

// --------------------------------------------------------------------- 1. title

{
  const s = slide();
  s.addText("PARISH ADMINISTRATION PLATFORM", {
    x: MARGIN, y: 2.1, w: 8, h: 0.35,
    fontFace: SANS, fontSize: 12, bold: true, color: MUTED, charSpacing: 3,
  });
  s.addText("Church App", {
    x: MARGIN, y: 2.5, w: 9, h: 1.1, fontFace: SERIF, fontSize: 54, bold: true, color: TEXT,
  });
  s.addText("Architecture & Design Overview", {
    x: MARGIN, y: 3.6, w: 9, h: 0.5, fontFace: SERIF, fontSize: 24, color: TEXT,
  });
  s.addText("Multi-tenant  ·  Spring Boot 4.1  ·  Thymeleaf  ·  MySQL 8  ·  Java 17", {
    x: MARGIN, y: 4.15, w: 9, h: 0.35, fontFace: SANS, fontSize: 13, color: MUTED,
  });
  s.addShape(pptx.ShapeType.line, {
    x: MARGIN, y: 4.75, w: 3.4, h: 0, line: { color: ACCENT_LIGHT, width: 1.5 },
  });
  s.addText(
    "One deployment serves many churches.\nEach parish sees only its own data.",
    { x: MARGIN, y: 4.95, w: 8, h: 0.8, fontFace: SANS, fontSize: 13, color: TEXT, lineSpacingMultiple: 1.3 },
  );
}

// ------------------------------------------------------------- 2. what it does

{
  const s = slide();
  heading(s, "What it does", "Six modules, one parish at a time");
  cards(s, [
    { title: "Anbiyam", body: "Basic communities: add, edit, soft delete.\n\nThe animator must be a member of that anbiyam — no one else." },
    { title: "Parish Priest", body: "An appointment history, not a roster.\n\nNo end date means currently serving. Appointing closes the last one." },
    { title: "Members", body: "Full record including sacraments.\n\nOne head per family, held in step with the family's own pointer." },
    { title: "Families & Payments", body: "Households, dues, receipts.\n\nMoney settles the oldest month first, always." },
  ]);
  footer(s, "Detail: docs/modules.md");
}

// -------------------------------------------------------------- 3. tenancy

{
  const s = slide();
  heading(s, "Multi-tenancy", "One database, many parishes");
  cards(s, [
    { title: "Reads", body: "A Hibernate filter restricts every query to one church.\n\nfindById is re-routed, because a primary-key load bypasses the filter entirely — the trap that makes this worth stating." },
    { title: "Writes", body: "The parish is stamped from the request's scope.\n\nNo form has a church field. A posted churchId is ignored, and a test posts one to prove it." },
    { title: "Platform staff", body: "A saas_user has no church, which is what lets it reach every parish.\n\nEntering one narrows it exactly as a parish account is narrowed." },
  ], { h: 3.6 });
  footer(s, "The filter must be enabled inside the transaction. Get that wrong and queries are silently unfiltered — no error, no warning.");
}

// ------------------------------------------------------------- 4. sign-in

{
  const s = slide();
  heading(s, "Security", "Two sign-in systems, one application");
  cards(s, [
    { title: "/login  →  member", body: "Parish accounts. Credentials live on the member row, which is why adding a member creates an account.\n\nEmail or mobile; mobiles normalised to +91 form." },
    { title: "/saas/login  →  saas_user", body: "Platform staff, with no church of their own.\n\nA parishioner presenting correct credentials here is refused: the separation is which table each chain reads, not the URL." },
  ], { h: 2.5 });
  rows(s, [
    { title: "Forced password change", body: "Every account is created on a system-assigned password and must replace it at first sign-in.", tag: "password_flag" },
    { title: "Lockout after five failures", body: "No automatic expiry — an administrator clears locked_at. Deliberate.", tag: "no auto-unlock" },
  ], { y: 4.9, rowH: 0.85 });
}

// --------------------------------------------------------- 5. permissions

{
  const s = slide();
  heading(s, "Authorisation", "Enforced, not decorated");
  cards(s, [
    { title: "The menu", body: "Built from the account's own authorities, so a module it cannot open is never offered.\n\nThis is presentation." },
    { title: "@PreAuthorize", body: "Every controller method carries its own check.\n\nThis is the door. A hidden link typed as a URL is refused with 403 — and tests post straight to those URLs." },
    { title: "The matrix", body: "287 rows across 12 resources, as data rather than code.\n\nChanging who may do what is an INSERT, with no migration and no redeploy." },
  ], { h: 3.2 });
  footer(s, "Parish staff see who their priest is but cannot appoint one. A volunteer collects money but can never edit or void the record of it.");
}

// ---------------------------------------------------------------- 6. payments

{
  const s = slide();
  heading(s, "Payments", "From what is owed to what was paid");
  flow(s, [
    { title: "Generate dues", body: "one month, every family" },
    { title: "Collect", body: "amount and mode" },
    { title: "Allocate", body: "oldest month first" },
    { title: "Receipt", body: "gapless, per parish" },
    { title: "Print", body: "58mm, bilingual" },
  ], { y: 2.4 });

  rows(s, [
    { title: "A collector never chooses which month money lands on", body: "So \"how far behind is this family?\" always has an answer without reading every row." },
    { title: "Paying beyond the arrears runs forward", body: "The next months are generated at the family's own rate and settled. Capped at two years." },
    { title: "Receipt numbers are locked for the length of the transaction", body: "Two collectors saving at once queue rather than colliding, and a rolled-back collection consumes no number." },
  ], { y: 4.15, rowH: 0.85 });
}

// ------------------------------------------------- 7. corrections and cutover

{
  const s = slide();
  heading(s, "Payments", "Mistakes, and starting from an old book");
  cards(s, [
    { title: "Void and reissue", body: "A receipt is never edited and never deleted.\n\nThe original is cancelled with a reason, its months restored, and a corrected receipt issued — cross-referenced both ways.\n\nThe number stays consumed. A gap in a receipt book cannot be told apart from a covered-up shortfall." },
    { title: "Opening balances", body: "A parish adopting this already has families who owe money.\n\nEach gets one line — everything owed before the system — dated the month before they start paying, so it settles first.\n\nEditable until money lands on it, then locked: the family holds a receipt." },
  ], { h: 3.6 });
  footer(s, "Why not simply edit the amount: the family is holding a printed slip, and changing the record behind it leaves the books disagreeing with their only evidence.");
}

// ------------------------------------------------------------------ 8. schema

{
  const s = slide();
  heading(s, "Data", "Owned by Flyway, V1 to V24");
  cards(s, [
    { title: "Three payment tables", body: "payment_due — what is owed\npayment — what came in\npayment_allocation — which settled which\n\nPlus a receipt sequence per parish per year." },
    { title: "Facts, not views", body: "A due is stored because it records what a family was charged that month.\n\nTotals, statuses and ages are derived. Store the fact, compute the view." },
    { title: "Corrections made", body: "category_id dropped: a substation is one because it has a parent.\n\ncatagory renamed family_role, and its contents replaced — it had held occupations." },
  ], { h: 3.4 });
  footer(s, "Hibernate runs ddl-auto: validate, so the application refuses to start if an entity and its table have drifted apart.");
}

// ------------------------------------------------------------------- 9. scale

{
  const s = slide();
  heading(s, "Scale", "Twenty parishes, twelve thousand families");
  cards(s, [
    { title: "~1.4M dues", body: "after ten years, across every parish" },
    { title: "~4M rows", body: "in the payment tables all told — small for MySQL" },
    { title: "≤ 6 queries", body: "per screen, whatever the page size" },
    { title: "0 growth", body: "in per-request cost as parishes are added" },
  ], { y: 2.15, h: 1.5 });

  rows(s, [
    { title: "Adding parishes does not multiply the work", body: "Every query carries church_id and is indexed for it, so one parish reads its own slice however many exist." },
    { title: "The risk was never row count — it was queries per row", body: "A list that queries once per row is correct, passes every test, and is fine against three families. At six hundred it is hundreds of round trips." },
    { title: "QueryCountTests counts the statements each screen issues", body: "It caught a lazy field the day it was written. A regression fails here rather than in a parish." },
  ], { y: 4.0, rowH: 0.85 });
}

// -------------------------------------------------------------- 10. open risks

{
  const s = slide();
  heading(s, "What needs attention", "Open risks");
  rows(s, [
    {
      title: "No way into a fresh deployment",
      body: "churchuat and churchprod have zero accounts, and every page is behind a login. Blocking for any real install.",
      tag: "Highest", tagColor: "E8A0A8",
    },
    {
      title: "600 families cannot be typed in",
      body: "The Excel import is designed but not built, and the family list is read-only. It must call the services, not the repositories.",
      tag: "High", tagColor: "E8A0A8",
    },
    {
      title: "Forgot-password restores a known password",
      body: "Anyone who can submit a mobile number can set that account to a value they know. Rate limited to three an hour; a one-time password would close it.",
      tag: "Medium",
    },
    {
      title: "No administrator unlock screen",
      body: "A locked parishioner needs someone to run SQL.",
      tag: "Medium",
    },
    {
      title: "Printing is unverified on real hardware",
      body: "Tamil should print — the browser sends graphics through the driver, not ESC/POS text — but no 58mm printer has been tried.",
      tag: "Medium",
    },
    // Five rows plus a footer is the tightest slide in the deck: 1.75 + 5×0.85 + 4×0.12
    // ends at 6.48, clear of the footer at 6.78.
  ], { y: 1.75, rowH: 0.85, gap: 0.12 });
  footer(s, "Solved since the last deck: tenant filtering is enforced on reads and writes · sample data no longer loads outside development");
}

// ----------------------------------------------------------------- 11. next

{
  const s = slide();
  heading(s, "Next", "In the order that unblocks a parish");
  flow(s, [
    { title: "Bootstrap admin", body: "a way into a fresh install" },
    { title: "Excel import", body: "load 600 families" },
    { title: "Family screens", body: "add and edit" },
    { title: "Reports", body: "collection and arrears" },
  ], { y: 2.6, h: 1.3 });

  cards(s, [
    { title: "Built and tested", body: "195 tests. Sign-in, permissions, tenancy, the four parish modules, the payment rules and their screens." },
    { title: "Not yet real", body: "No parish can use this until a fresh deployment can be signed into and its register loaded. Those two, then it is usable." },
  ], { y: 4.4, h: 2.0 });
}

// ------------------------------------------------------------------- write it

const out = path.resolve(__dirname, "..", "church-app-overview.pptx");
pptx.writeFile({ fileName: out }).then(() => {
  console.log("wrote " + out);
});
