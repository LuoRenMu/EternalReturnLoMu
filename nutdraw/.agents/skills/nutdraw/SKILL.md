---
name: nutdraw
description: Design, implement, review, or repair NutDraw image templates by first modelling the intended layout as HTML/CSS and then translating it into NutDraw's Kotlin DSL. Use for NutDraw template creation, visual layout changes, image-card rendering, text or image overflow bugs, incorrect height or horizontal alignment, rounded-corner defects, and output-canvas boundary checks.
---

# NutDraw

Treat every NutDraw image as a fixed-size web page. Establish the HTML/CSS mental model and its box geometry before writing or changing Kotlin DSL code.

## Workflow

### 1. Fix the canvas contract

- Record the final `TemplateDocument(width, height, ...)` dimensions first.
- Give the root document the same explicit width and height.
- Treat the canvas as a hard clipping boundary. Never rely on content outside it being harmless.
- Identify all variable-length data before choosing fixed dimensions: text, lists, optional blocks, remote images, and fallback states.

### 2. Imagine the HTML/CSS layout

Describe the intended structure as nested HTML boxes before converting it. Write a compact HTML/CSS sketch even when no canonical web implementation exists. If a canonical page does exist, inspect its DOM, computed styles, viewport, fonts, and representative data rather than guessing from a screenshot. For each box, decide:

- normal flow or absolute positioning;
- row or column direction;
- width, height, padding, gap, and border;
- alignment on both axes;
- background, image fitting, and corner radii;
- text wrapping, line height, maximum lines, truncation, and empty state.

Use normal flex flow by default. Use absolute positioning only for deliberate overlays whose containing block and all four edge constraints are understood.

### 3. Prove the box budget

Calculate the intended HTML/CSS occupied size before implementation:

```text
outer width  = content width  + left/right padding + left/right border
outer height = content height + top/bottom padding + top/bottom border
row usage    = sum(child outer widths) + gaps
column usage = sum(child outer heights) + gaps
```

Require every container to satisfy:

```text
row usage <= inner width
column usage <= inner height
x >= 0, y >= 0
x + outer width <= canvas width
y + outer height <= canvas height
```

Include margins, padding, borders, gaps, wrapping rows, and absolutely positioned children in the calculation. Do not compensate for an overflow by casually increasing the canvas; first verify the intended HTML/CSS proportions.

Then translate the budget using NutDraw's actual engine semantics:

- resolved `width` and `height` are allocated sizes, not browser content-box sizes;
- `margin` shrinks and offsets the painted `LayoutBox` inside its allocated size;
- `padding` shrinks the inner area available to children;
- `border` is painted as a stroke and consumes no layout space, so allow for half its stroke at visual edges;
- percentages resolve against the parent's inner area;
- `gap` is added between allocated child sizes;
- absolutely positioned children use the parent's padded inner area and do not contribute to normal-flow height;
- free space is clamped to zero, so an overfull row or column can silently overflow instead of compressing.

For containment checks, compare actual `LayoutBox.bounds` recursively. Do not assume browser box-sizing behavior.

### 4. Convert deliberately to NutDraw

Map the model directly:

- HTML container -> `Document`, `Row`, `Column`, or `element`
- CSS box metrics -> `CssStyle(width, height, padding, gap, border)`
- flex rules -> `direction`, `wrap`, `justifyContent`, `alignItems`, `flexGrow`
- positioned overlay -> `position`, `left`, `right`, `top`, `bottom`
- image fitting -> explicit image width/height and `objectFit`
- border radius -> `borderRadius` or `cornerRadii`

Keep the parent-child hierarchy aligned with the imagined HTML. Avoid scattered offsets that merely imitate the target screenshot at one data size.

### 5. Contain text and images

- Assign every image a bounded box and an intentional fit mode. Preserve aspect ratio unless distortion is explicitly desired.
- Reserve text width after subtracting siblings, gaps, padding, and borders.
- Reserve text height from `lineHeight * allowed lines`; account for font metrics and fallback fonts.
- Choose an explicit policy for long text: pre-truncate/ellipsize, shrink within a safe minimum, or expand the containing layout and canvas coherently. Current NutDraw text renders one line and clips to its own bounds; do not claim wrapping or automatic ellipsis unless the engine gains those capabilities.
- Test empty, typical, and longest realistic content. Include long CJK text and long unbroken Latin/numeric strings.
- Never allow a child image or text node to extend beyond its owning `NutElement`.

### 6. Audit height and horizontal placement

After every structural change, re-check:

- total root height against all vertical sections and gaps;
- each section's actual top and bottom edges;
- left/right symmetry and intended alignment;
- percentage widths against the parent's inner width, not the canvas width;
- centered elements after sibling widths and gaps are included;
- absolute elements against the correct containing element.

Compare the result with the original HTML/CSS model. If the geometry differs, fix the model-to-DSL mapping instead of adding unexplained pixel nudges.

### 7. Audit rounded corners

- When an outer container is rounded, inspect its background, border, background image, and every edge-touching child.
- Remember that NutDraw clips an element's own background image and a `NutImage` to their own rounded shapes, but a rounded parent does not clip descendant elements.
- Ensure no square child background or border remains visible outside or on top of a rounded outer corner.
- Match child corner radii to the corners they occupy, or inset/clip the child so the outer rounded silhouette remains intact.
- Check each corner independently when using `cornerRadii`.
- Render against a contrasting background so leaked straight edges and square corners are visible.

### 8. Render and verify

Do not finish from code inspection alone.

1. Run the narrowest layout or regression tests for the template.
2. Render representative previews for minimum, typical, and maximum content.
3. Inspect the complete image at its actual output dimensions.
4. Check all four canvas edges, every rounded outer edge, text baselines, image bounds, section heights, and left/right positions.
5. Add or update regression assertions for important bounds and relationships when the project exposes `FlexLayoutEngine` or node IDs.

Use a recursive assertion for parent containment, allowing only an explicitly documented tolerance or intentional overlay:

```kotlin
fun assertContained(box: LayoutBox) {
    box.children.forEach { child ->
        assertTrue(child.bounds.left >= box.bounds.left)
        assertTrue(child.bounds.top >= box.bounds.top)
        assertTrue(child.bounds.right <= box.bounds.right)
        assertTrue(child.bounds.bottom <= box.bounds.bottom)
        assertContained(child)
    }
}
```

For HTML/CSS parity, use the same viewport, fonts, assets, and deterministic empty/typical/maximum fixtures. State any pixel tolerance before comparing renders.

Finish only when no image, text, border, background, or positioned element exceeds its parent or the preset canvas size, and the rendered geometry still matches the intended HTML/CSS layout.

## Final checklist

- Canvas and root dimensions match.
- HTML/CSS box model was established before conversion.
- Width and height budgets include padding, borders, and gaps.
- Variable text and images have explicit containment policies.
- Heights and left/right positions match the model.
- Rounded containers expose no straight outer edge or square-corner child.
- Every element remains inside its parent and the canvas.
- Representative previews were rendered and visually inspected.
