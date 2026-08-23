/**
 * The artwork registry: the one place that decides which file a piece of art resolves to.
 *
 * The chrome is hand-authored SVG; the dragon, the backdrop and the wordmark are painted rasters.
 * Either can replace the other in place — a file with the same stem wins on format, best first —
 * without touching a component or this list, which is why nothing here is imported by name.
 */
const FILES = import.meta.glob('./art/**/*.{svg,png,webp,avif}', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>

/** Best first. A format later in this list never displaces one earlier. */
const PRECEDENCE = ['avif', 'webp', 'png', 'svg']

const ART = ((): Record<string, string> => {
  const best: Record<string, { rank: number; url: string }> = {}
  for (const [path, url] of Object.entries(FILES)) {
    const match = /\.\/art\/([^/]+)\/(.+)\.([^.]+)$/.exec(path)
    if (!match) {
      continue
    }
    const [, folder, stem, extension] = match
    const rank = PRECEDENCE.indexOf(extension)
    const key = `${folder}/${stem}`
    if (rank >= 0 && (best[key] === undefined || rank < best[key].rank)) {
      best[key] = { rank, url }
    }
  }
  return Object.fromEntries(Object.entries(best).map(([key, { url }]) => [key, url]))
})()

const art = (key: string): string => {
  const url = ART[key]
  if (url === undefined) {
    throw new Error(`No artwork for ${key}`)
  }
  return url
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

export const crestArt = (faction: Faction): string => art(`crests/${faction}`)

export const iconArt = (name: IconName): string => art(`icons/${name}`)

export const backdropArt = art('scene/backdrop')

export const wordmarkArt = art('title/wordmark')
