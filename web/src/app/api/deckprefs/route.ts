// The cards' own tags and the saved card packages (synced prefs cardTags and deckPackages, app 1.97): reading them,
// changing them and the decks that use each package. Deck pages and the package dialogs call this.
import { currentAccount, errorResponse, fromOurPages, json } from "@/lib/auth";
import { previewText } from "@/lib/deckimport";
import { nameKey } from "@/lib/decknotes";
import { encodePackages, MAX_PACKAGES, normalisePackage, packageUse, parsePackages, type DeckPackage } from "@/lib/deckpackages";
import { encodeCardTags, normaliseTags, parseCardTags } from "@/lib/deckroles";
import { syncDecks } from "@/lib/decks";
import { editSync, loadSync } from "@/lib/store";
import { prefs, setPrefs } from "@/lib/syncfile";
import { EditError } from "@/lib/wishlist";

export const dynamic = "force-dynamic";

const str = (value: unknown, max: number) => (typeof value === "string" ? value.slice(0, max) : "");
const list = (value: unknown, max: number) => (Array.isArray(value) ? value.filter((x): x is string => typeof x === "string").slice(0, max) : []);

/** The packages and the decks that use each of them. */
function useOf(packages: DeckPackage[], decks: ReturnType<typeof syncDecks>): Record<string, { full: number[]; partly: number[] }> {
  return Object.fromEntries(packages.map((pkg) => [pkg.id, packageUse(pkg, decks)]));
}

/** The tags and packages, and how much of each package each deck has. */
export async function GET() {
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const { file } = await loadSync(a);
    const p = prefs(file);
    const packages = parsePackages(p.deckPackages);
    return json({ cardTags: parseCardTags(p.cardTags), packages, use: useOf(packages, syncDecks(file)) });
  } catch (e) {
    return errorResponse(e);
  }
}

/**
 * set-tags: one card's tags; bulk-tags: the same tag added to or removed from many cards; save-package (a new id when
 * missing); package-from-text: a package made from a pasted decklist; rename-package and delete-package.
 */
export async function POST(req: Request) {
  if (!fromOurPages(req)) return json({ error: "Not allowed." }, 403);
  const a = await currentAccount();
  if (!a) return json({ error: "Log in first.", loggedOut: true }, 401);
  try {
    const body = ((await req.json()) ?? {}) as {
      action?: string;
      name?: string;
      tags?: unknown;
      names?: unknown;
      add?: unknown;
      remove?: unknown;
      id?: string;
      cards?: unknown;
      text?: string;
    };
    // A pasted list is looked up on Scryfall first, like adding it to a deck.
    if (body.action === "package-from-text") {
      const name = str(body.name, 200).trim().slice(0, 60);
      if (!name) throw new EditError("Give the package a name.");
      const preview = await previewText(str(body.text, 60_000));
      const saved = await editSync(a, (f, now) => {
        const packages = parsePackages(prefs(f).deckPackages);
        if (packages.length >= MAX_PACKAGES) throw new EditError(`You have ${MAX_PACKAGES} packages already.`);
        // The same card twice in the text adds its quantities together.
        const cards = new Map<string, { name: string; scryfallId: string; quantity: number }>();
        preview.result.lines.forEach((line, i) => {
          const found = preview.found[i];
          if (!found) return;
          const id = found.card.scryfallId;
          const had = cards.get(id);
          if (had) had.quantity = Math.min(999, had.quantity + line.quantity);
          else cards.set(id, { name: found.card.name, scryfallId: id, quantity: line.quantity });
        });
        const pkg = normalisePackage({ id: crypto.randomUUID(), name, cards: [...cards.values()] });
        if (!pkg) throw new EditError("That list made no package.");
        const next = [...packages, pkg];
        setPrefs(f, { deckPackages: encodePackages(next) }, now);
        return { package: pkg, notFound: preview.notFound, packages: next };
      });
      return json({ ok: true, ...saved });
    }
    const result = await editSync(a, (f, now) => {
      if (body.action === "set-tags") {
        const name = str(body.name, 200).trim();
        if (!name) throw new EditError("Pick a card first.");
        const tags = parseCardTags(prefs(f).cardTags);
        const cleaned = normaliseTags(list(body.tags, 50));
        if (cleaned.length > 0) tags[nameKey(name)] = cleaned;
        else delete tags[nameKey(name)];
        setPrefs(f, { cardTags: encodeCardTags(tags) }, now);
        return { cardTags: tags };
      }
      if (body.action === "bulk-tags") {
        const names = list(body.names, 2000);
        const add = str(body.add, 100).trim();
        const remove = str(body.remove, 100).trim();
        if (!add && !remove) throw new EditError("Give a tag to add or remove.");
        const tags = parseCardTags(prefs(f).cardTags);
        for (const raw of names) {
          const name = raw.trim();
          if (!name) continue;
          const key = nameKey(name);
          const had = tags[key] ?? [];
          let next = normaliseTags(add ? [...had, add] : had);
          if (remove) next = next.filter((tag) => tag.toLowerCase() !== remove.toLowerCase());
          if (next.length > 0) tags[key] = next;
          else delete tags[key];
        }
        setPrefs(f, { cardTags: encodeCardTags(tags) }, now);
        return { cardTags: tags };
      }
      if (body.action === "save-package") {
        const id = str(body.id, 100).trim();
        const name = str(body.name, 200).trim();
        if (!name) throw new EditError("Give the package a name.");
        const pkg = normalisePackage({ id: id || crypto.randomUUID(), name, cards: body.cards });
        if (!pkg) throw new EditError("That package can't be saved.");
        const packages = parsePackages(prefs(f).deckPackages);
        const at = id ? packages.findIndex((p) => p.id === id) : -1;
        let next: DeckPackage[];
        if (at >= 0) next = packages.map((p, i) => (i === at ? pkg : p));
        else {
          if (packages.length >= MAX_PACKAGES) throw new EditError(`You have ${MAX_PACKAGES} packages already.`);
          next = [...packages, pkg];
        }
        setPrefs(f, { deckPackages: encodePackages(next) }, now);
        return { packages: next };
      }
      if (body.action === "rename-package") {
        const id = str(body.id, 100).trim();
        const name = str(body.name, 200).trim().slice(0, 60);
        if (!name) throw new EditError("Give the package a name.");
        const packages = parsePackages(prefs(f).deckPackages);
        if (!packages.some((p) => p.id === id)) throw new EditError("That package isn't there any more.");
        const next = packages.map((p) => (p.id === id ? { ...p, name } : p));
        setPrefs(f, { deckPackages: encodePackages(next) }, now);
        return { packages: next };
      }
      if (body.action === "delete-package") {
        const id = str(body.id, 100).trim();
        const packages = parsePackages(prefs(f).deckPackages);
        const next = packages.filter((p) => p.id !== id);
        if (next.length === packages.length) throw new EditError("That package isn't there any more.");
        setPrefs(f, { deckPackages: encodePackages(next) }, now);
        return { packages: next };
      }
      throw new EditError("Unknown action.");
    });
    return json({ ok: true, ...result });
  } catch (e) {
    return errorResponse(e);
  }
}
