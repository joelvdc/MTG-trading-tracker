// Modal double-faced cards whose back face is a land (e.g. "Sink into Stupor // Soporific Springs", "Instant // Land").
// Archidekt counts them in the Land section's heading in brackets ("Qty: 37 (40 w/ MDFC)"); these pure helpers find them.

/** What these helpers need of a deck line. */
interface MdfcRow {
  quantity: number;
  maybeboard?: boolean;
}

/** Copies of main-deck cards (not maybeboard) whose front face isn't a land but whose back face is. */
export function mdfcLandCopies<T extends MdfcRow>(rows: T[], typeLineOf: (row: T) => string | null | undefined): number {
  return rows.reduce((sum, r) => (r.maybeboard ? sum : sum + (backIsLand(typeLineOf(r) ?? "") ? r.quantity : 0)), 0);
}

/** Whether a card's type line is a spell on the front and a land on a later face ("Instant // Land"). */
export function backIsLand(typeLine: string): boolean {
  const faces = typeLine.split(" // ");
  const isLand = (face: string) => /\bLand\b/.test(face);
  return faces.length > 1 && !isLand(faces[0]) && faces.slice(1).some(isLand);
}

/** Whether a section title is the Land section ("Land" or "Lands", any case). */
export function isLandSection(title: string): boolean {
  return /^lands?$/i.test(title.trim());
}
