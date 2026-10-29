# jotty-android artwork

Dark flat-vector branding for README / GitLab avatar / social share.

**Palette:** accent `#8b5cf6` · secondary `#c4b5fd` · canvas `#12081f`

## Layouts

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
