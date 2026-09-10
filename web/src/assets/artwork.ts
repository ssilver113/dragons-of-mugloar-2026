/**
 * The artwork registry: the one place that decides which file a piece of art resolves to.
 *
 * The chrome is hand-authored SVG; the dragon, the backdrop and the wordmark are painted rasters.
 * Either can replace the other in place — a file with the same stem wins on format, best first —
 * without touching a component or this list, which is why nothing here is imported by name.
 *
 * A stem may be drawn at several widths, declared in the filename as `wordmark@800.webp`. They
 * belong to the same stem: the widest is what `art` returns, and the whole set is the `srcset`.
 * So a responsive set is added or dropped by adding or dropping files, exactly like a format is.
 */
const FILES = import.meta.glob('./art/**/*.{svg,png,webp,avif}', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>

/** Best first. A format later in this list never displaces one earlier. */
const PRECEDENCE = ['avif', 'webp', 'png', 'svg']

/** A width of 0 is a file that declared none, which is every stem drawn only once. */
type Drawing = { rank: number; width: number; url: string }

const DRAWINGS = ((): Record<string, Drawing[]> => {
  const found: Record<string, Drawing[]> = {}
  for (const [path, url] of Object.entries(FILES)) {
    const match = /\.\/art\/([^/]+)\/(.+?)(?:@(\d+))?\.([^.]+)$/.exec(path)
    if (!match) {
      continue
    }
    const [, folder, stem, width, extension] = match
    const rank = PRECEDENCE.indexOf(extension)
    if (rank < 0) {
      continue
    }
    const key = `${folder}/${stem}`
    found[key] = [...(found[key] ?? []), { rank, width: Number(width ?? 0), url }]
  }

  // One format per stem, widest first: mixing formats inside a set would let the precedence
  // above be decided by whichever width the browser happened to want.
  return Object.fromEntries(
    Object.entries(found).map(([key, drawings]) => {
      const best = Math.min(...drawings.map((drawing) => drawing.rank))
      return [
        key,
        drawings.filter((drawing) => drawing.rank === best).sort((a, b) => b.width - a.width),
      ]
    }),
  )
})()

const ART: Record<string, string> = Object.fromEntries(
  Object.entries(DRAWINGS).map(([key, drawings]) => [key, drawings[0].url]),
)

const art = (key: string): string => {
  const url = ART[key]
  if (url === undefined) {
    throw new Error(`No artwork for ${key}`)
  }
  return url
}

/**
 * Undefined rather than an empty string when a stem was drawn once, so the attribute is absent
 * instead of present and empty.
 */
const artSrcset = (key: string): string | undefined => {
  const set = (DRAWINGS[key] ?? [])
    .filter((drawing) => drawing.width > 0)
    .map((drawing) => `${drawing.url} ${drawing.width}w`)
  return set.length > 1 ? set.join(', ') : undefined
}

export type DragonMood = 'idle' | 'victorious' | 'defeated'
export type Faction = 'people' | 'state' | 'underworld'

/**
 * The chrome marks: a figure's icon, a section's icon. Drawn in the same hand as the crests and
 * kept to three or four shapes each, because these render at eighteen pixels rather than forty.
 */
export type IconName =
  | 'score'
  | 'gold'
  | 'life'
  | 'level'
  | 'turn'
  | 'board'
  | 'shop'
  | 'log'
  | 'standing'
  | 'advisor'
  | 'autoplay'

/**
 * The icon for a shop item.
 *
 * Every id recon saw has its own drawing. An id it did not see falls back on what the item
 * *does* rather than on what it is called — which is also how the rest of the app describes a
 * purchase, since the names are flavour and the price is what carries the effect.
 */
export function itemArt(itemId: string, livesGained: number, levelsGained: number): string {
  const known = ART[`items/${itemId}`]
  if (known !== undefined) {
    return known
  }
  if (livesGained > 0) {
    return art('items/hpot')
  }
  if (levelsGained >= 2) {
    return art('items/iron')
  }
  if (levelsGained === 1) {
    return art('items/wax')
  }
  return art('items/unknown')
}

export const dragonArt = (mood: DragonMood): string => art(`dragon/${mood}`)

/**
 * The advisor. A second dragon rather than a person: the only character the game has is a dragon,
 * and an old one at a writing desk says "this is your dragon's counsel" without a caption.
 */
export const advisorArt = art('advisor/scribe')

export const crestArt = (faction: Faction): string => art(`crests/${faction}`)

export const iconArt = (name: IconName): string => art(`icons/${name}`)

export const backdropArt = art('scene/backdrop')

export const backdropSrcset = artSrcset('scene/backdrop')

export const wordmarkArt = art('title/wordmark')

export const wordmarkSrcset = artSrcset('title/wordmark')
