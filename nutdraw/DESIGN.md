# NutDraw design

NutDraw follows the responsibility boundaries of KarenBot's Shinobu module at
`bb8be4c5848e15bd4ecd1b60451948bf83659f93`, while retaining features required
by this project that Shinobu does not provide.

## Layers

- `dom`: immutable HTML-like render tree. Every node type and the Kotlin DSL builder have separate implementations.
- `css`: CSS value model split into layout, text, sizing, spacing and border modules. It performs no I/O.
- `layout`: deterministic measurement and Flex/absolute positioning.
- `resource`: renderer-scoped ownership of image and font modules.
- `render`: Skia painting plus deep resource pipelines. Fonts separate fallback configuration, typeface resolution and run construction. Images separate source adapters, byte caching, format detection and raster/SVG decoding.
- `template`: template lifecycle management. Domain templates build documents; the
  manager performs build → resource resolution → layout → draw.
- `templates`: domain-specific Eternal Return page definitions.

## Rules

1. Templates never open files, download images, choose fonts, or call Skia canvases.
2. DOM nodes carry semantic IDs when the source HTML has IDs; tests may query paths
   such as `body/left/rank/rank_stats/rank_canvas`.
3. Style values are explicit and immutable. Shared styles are copied, not mutated.
4. Layout is deterministic and independent from painting or network state.
5. Resources are owned by one `ResourceManager` per renderer and cached there.
6. Custom visuals such as charts are first-class DOM nodes with dedicated painters.
7. Existing SVG, CJK fallback, absolute positioning, per-corner radii and retained
   preview tests are compatibility requirements.
