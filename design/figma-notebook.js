// Guard against duplicating a populated reference; later edits should target returned node IDs.
for (const frame of FOUNDATION.frames) {
  const node = await figma.getNodeByIdAsync(frame.id);
  if (node.children.length) throw new Error("Reference already populated; update its existing layers in place.");
}
// Editable screen construction. Foundation IDs are recorded in figma-state.json.
await Promise.all([
  { family: "Lora", style: "Regular" }, { family: "Lora", style: "Italic" },
  { family: "Caveat", style: "Regular" }, { family: "Roboto", style: "Regular" },
].map(font => figma.loadFontAsync(font)));
const ids = [];
const variables = {};
for (const [name, id] of Object.entries(FOUNDATION.variables)) {
  variables[name] = await figma.variables.getVariableByIdAsync(id);
}
const track = node => { ids.push(node.id); return node; };
const paint = name => [figma.variables.setBoundVariableForPaint(
  { type: "SOLID", color: { r: 1, g: 1, b: 1 } }, "color", variables["color/" + name],
)];
function text(parent, name, value, style, color = "ink", width) {
  const node = track(figma.createText());
  node.name = name; node.textStyleId = FOUNDATION.styles[style];
  node.characters = value; node.fills = paint(color); node.textAutoResize = "HEIGHT";
  if (width) node.resize(width, style === "Title" ? 76 : 20);
  parent.appendChild(node); if (style === "Title") node.layoutSizingVertical = "FIXED"; return node;
}
function layout(parent, name, direction, width, height) {
  const node = track(figma.createAutoLayout(direction));
  node.name = name; node.fills = []; node.resize(width, height);
  node.primaryAxisSizingMode = "FIXED"; node.counterAxisSizingMode = "FIXED";
  parent.appendChild(node); return node;
}
// Reimport remote components in each execution: their virtual node IDs are not durable.
const iconSet = await figma.importComponentSetByKeyAsync("dee6932fb2a593bf051991ec7b89c17491b63774");
const iconButton = iconSet.children.find(node => node.name === "Type=Square, Size=Small, Width=Default, State=Enabled");
const svgPaths = {
  Book: '<path d="M12 5C9 3 5 3 2 4V20C5 19 9 19 12 21C15 19 19 19 22 20V4C19 3 15 3 12 5ZM12 5V21"/>',
  More: '<circle cx="12" cy="5" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="12" cy="19" r="1"/>',
  Previous: '<path d="M20 12H4m7-7-7 7 7 7"/>',
  Next: '<path d="M4 12h16m-7-7 7 7-7 7"/>',
};
const icons = {}; let x = 2100;
for (const [name, path] of Object.entries(svgPaths)) {
  const component = track(figma.createComponent());
  component.name = "Afterglow/Icon/" + name; component.description = "Native notebook " + name.toLowerCase() + " action";
  component.resize(24, 24); component.fills = []; component.x = x; component.y = 200; x += 60;
  const svg = figma.createNodeFromSvg('<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#8B7F72" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" xmlns="http://www.w3.org/2000/svg">' + path + '</svg>');
  component.appendChild(svg); ids.push(svg.id, ...svg.findAll(() => true).map(node => node.id)); icons[name] = component;
}
function action(parent, kind) {
  const instance = track(iconButton.createInstance()); instance.name = "Action/" + kind;
  const swap = Object.entries(instance.componentProperties).find(([, prop]) => prop.type === "INSTANCE_SWAP")[0];
  instance.setProperties({ [swap]: icons[kind].id }); instance.resize(48, 48);
  parent.appendChild(instance); return instance;
}
const lucid = track(figma.createComponent());
lucid.name = "Afterglow/LucidityNote"; lucid.description = "Circled pen annotation; native radio semantics.";
lucid.layoutMode = "HORIZONTAL"; lucid.fills = []; lucid.resize(290, 48);
lucid.primaryAxisSizingMode = "FIXED"; lucid.counterAxisSizingMode = "FIXED";
lucid.counterAxisAlignItems = "CENTER"; lucid.itemSpacing = 12; lucid.x = 2100; lucid.y = 300;
for (const word of ["Dreaming:", "lucid", "/", "non-lucid"]) text(lucid, "Annotation/" + word, word, "Annotation", word === "lucid" ? "ink" : "pencil");
const mark = track(figma.createEllipse()); lucid.appendChild(mark); mark.layoutPositioning = "ABSOLUTE";
mark.resize(52, 27); mark.x = 88; mark.y = 9; mark.rotation = 5;
mark.fills = []; mark.strokes = paint("pencil"); mark.strokeWeight = .8;
const newDream = track(figma.createComponent());
newDream.name = "Afterglow/NewDream"; newDream.description = "Create an empty dream; no generated title or tags.";
newDream.layoutMode = "HORIZONTAL"; newDream.fills = []; newDream.resize(112, 48);
newDream.primaryAxisSizingMode = "FIXED"; newDream.counterAxisSizingMode = "FIXED";
newDream.counterAxisAlignItems = "CENTER"; newDream.primaryAxisAlignItems = "CENTER";
newDream.x = 2100; newDream.y = 380; text(newDream, "New dream label", "+  New dream", "Label");
const screens = [];
for (const screen of FOUNDATION.frames) {
  const frame = await figma.getNodeByIdAsync(screen.id);
  const { w, h } = screen; const landscape = w > h;
  frame.itemSpacing = 0; frame.paddingLeft = 0; frame.paddingRight = 0; frame.paddingTop = 0; frame.paddingBottom = 0;
  const background = track(figma.createFrame()); background.name = "Paper / edge-to-edge";
  frame.appendChild(background); background.layoutPositioning = "ABSOLUTE";
  background.resize(w, h); background.x = 0; background.y = 0; background.fills = paint("ivory"); background.clipsContent = true;
  function rule(rx, ry, rw, rh, name, color) {
    const node = track(figma.createRectangle()); node.name = name; background.appendChild(node);
    node.resize(rw, rh); node.x = rx; node.y = ry; node.fills = paint(color);
    node.opacity = color === "rule" ? .52 : .5; return node;
  }
  for (let y = 146; y < h - 64; y += 30) rule(0, y, w, .5, "Notebook ruling", "rule");
  rule(28, 68, .7, h - 126, "Left margin", "margin");
  if (landscape) {
    rule(w / 2, 0, 1, h, "Spine", "pencil").opacity = .14;
    rule(w / 2 + 28, 68, .7, h - 126, "Right margin", "margin");
  }
  const header = layout(frame, "On-paper toolbar", "HORIZONTAL", w, 56);
  header.layoutSizingHorizontal = "FILL"; header.counterAxisAlignItems = "CENTER";
  header.paddingLeft = 24; header.paddingRight = 16; header.itemSpacing = 4;
  header.setBoundVariable("paddingLeft", variables["spacing/24"]);
  const wordmark = text(header, "Wordmark", "Dream Diary", "Wordmark", "pencil"); wordmark.layoutSizingHorizontal = "FILL";
  action(header, "Book"); header.appendChild(track(newDream.createInstance())); action(header, "More");
  const main = layout(frame, "Notebook leaves", "HORIZONTAL", w, h - 112);
  main.layoutSizingHorizontal = "FILL"; main.layoutSizingVertical = "FILL";
  const left = layout(main, landscape ? "Left leaf" : "Writing leaf", "VERTICAL", landscape ? w / 2 : w, h - 112);
  left.layoutSizingHorizontal = "FILL"; left.layoutSizingVertical = "FILL";
  left.paddingLeft = 48; left.paddingRight = 32; left.paddingTop = 16; left.paddingBottom = 20; left.itemSpacing = 14;
  left.setBoundVariable("paddingRight", variables["spacing/32"]);
  text(left, "Date", "21 OCTOBER 2026", "Label", "pencil");
  text(left, "Dream title", "The house above\nthe clouds", "Title", "ink", (landscape ? w / 2 : w) - 80);
  if (landscape) {
    const space = layout(left, "Quiet writing space", "VERTICAL", w / 2 - 80, 20); space.layoutSizingVertical = "FILL";
    text(left, "Folio", "01", "Label", "pencil");
    const right = layout(main, "Right leaf / dream", "VERTICAL", w / 2, h - 112);
    right.layoutSizingHorizontal = "FILL"; right.layoutSizingVertical = "FILL";
    right.paddingLeft = 48; right.paddingRight = 32; right.paddingTop = 16; right.paddingBottom = 20; right.itemSpacing = 14;
    text(right, "Entry label", "WHAT I REMEMBER", "Label", "pencil");
    const body = text(right, "Dream body", "I knew every room, even though I had never been there before.\n\nSomewhere downstairs, someone was making tea.", "Body", "ink", w / 2 - 80);
    body.layoutSizingVertical = "FILL"; right.appendChild(track(lucid.createInstance()));
  } else {
    text(left, "Entry label", "WHAT I REMEMBER", "Label", "pencil");
    const body = text(left, "Dream body", "I knew every room, even though I had never been there before.\n\nThe windows were open and the curtains moved like they were breathing.\n\nSomewhere downstairs, someone was making tea.", "Body", "ink", w - 80);
    body.layoutSizingVertical = "FILL"; left.appendChild(track(lucid.createInstance()));
  }
  const footer = layout(frame, "On-paper pagination", "HORIZONTAL", w, 56);
  footer.layoutSizingHorizontal = "FILL"; footer.counterAxisAlignItems = "CENTER"; footer.paddingLeft = 16; footer.paddingRight = 16;
  action(footer, "Previous");
  const count = text(footer, "Page counter", "Dream 1 of 3", "Label", "pencil"); count.layoutSizingHorizontal = "FILL"; count.textAlignHorizontal = "CENTER";
  action(footer, "Next");
  const all = frame.findAll(() => true); const counts = {};
  for (const node of all) counts[node.type] = (counts[node.type] || 0) + 1;
  screens.push({ id: frame.id, name: frame.name, width: frame.width, height: frame.height,
    descendants: all.length, counts,
    imageNodes: all.filter(node => Array.isArray(node.fills) && node.fills.some(fill => fill.type === "IMAGE")).map(node => node.id),
    fonts: [...new Set(frame.findAllWithCriteria({ types: ["TEXT"] }).map(node => node.fontName.family))],
  });
}
return { createdNodeIds: ids, mutatedNodeIds: FOUNDATION.frames.map(frame => frame.id),
  components: { lucidity: lucid.id, newDream: newDream.id, icons: Object.fromEntries(Object.entries(icons).map(([name, node]) => [name, node.id])) }, screens };
