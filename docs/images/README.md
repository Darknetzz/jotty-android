# jotty-android artwork

Dark flat-vector branding for README / GitLab avatar / social share.

**Palette:** accent `#8b5cf6` · secondary `#c4b5fd` · canvas `#12081f`

## App-icon style (current)

Used by the project README. Flat Jotty purple (`#9E5DF8`) with the app icon's white J and lavender (`#E4D4FF`) check mark. Hand-made SVGs, not produced by `batch_generate.py`.

| File | Size |
|------|------|
| `logo-icon.png` / `.svg` | 512×512 |
| `banner-icon.png` / `.svg` | 1280×640 |

Render a PNG from its SVG with headless Chrome (the banner text needs Segoe UI):

```bash
chrome --headless=new --window-size=1280,640 --screenshot=banner-icon.png banner-icon.svg
```

## Layouts (generated, older robot mark)

### Adb-keeper style (root)

| File | Size |
|------|------|
| `logo-{solid,gradient,transparent}.png` | 512×512 |
| `banner-{solid,gradient,transparent}.png` | 1280×640 |

### Code style

| Path | Contents |
|------|----------|
| `repo/` | Transparent 1×1 / 16×9 (+ label) |
| `bg-solid/repo/` | Solid dark canvas variants |
| `bg-gradient/repo/` | Gradient + glow variants |
| `og/jotty-android.png` | Open Graph 1280×640 |
| `svg-src/` | SVG sources |
| `ai/` | Optional AI atmospheric banner |

## Regenerate

```bash
python batch_generate.py --only jotty-android
```
