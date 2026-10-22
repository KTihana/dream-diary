# Dream Diary design references

- [Editable Figma screens](https://www.figma.com/design/uoARmOoe4JxzxpfwVKhcOA): landscape `2:24`, portrait `2:25`.
- [Canva visual direction board](https://canva.link/6c3hzl7rhsxqnbr): paper-first composition, typographic hierarchy, muted rules, warm ink, integrated chrome, and portrait reflow.

The Figma screens use editable layers, auto-layout, reusable annotation/action components, Material 3 standard icon-button instances, scoped color/spacing variables, and text styles. Both screens were checked for image-free editable structure and the expected Lora / Roboto / Caveat fonts. The portrait title was given an explicit two-line height after visual review caught overlapping text.

The implementation combines the Canva board's paper-first direction with the Figma screens. It uses Lora for readable serif titles and text, Roboto for labels, and Caveat for the circled lucid annotation. All font files are bundled for offline use. The font licenses are included here and in the APK's assets.

`figma-state.json` records created IDs and the design URLs. `figma-notebook.js` is the initial construction script; it expects `FOUNDATION` from that ledger and must not be rerun against populated frames. Update the existing frames in place.

The paper and its central gutter use full-window coordinates in Android. Camera-cutout insets apply to text and controls individually, so the left leaf stays at x=0, the gutter stays at half the display width, and the right leaf reaches the right display edge. Device tests assert those leaf bounds as well as the full-screen background.

The portrait revision moves the actions into one compact bottom row, uses a 34 sp title and 18 sp text with 32 sp leading, and narrows page gutters to 32 / 24 dp. The date sits near the top-left edge; the title clears the camera cutout. The landscape composition keeps its existing typography and controls. Updated Figma IDs and styles are recorded in `portrait-revision.json`.
