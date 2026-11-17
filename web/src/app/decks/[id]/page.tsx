"use client";

import { useParams } from "next/navigation";
import { DeckScreen } from "@/components/DeckScreen";

/** One deck: the deck page itself, which DeckScreen draws (it draws a precon's preview too). */
export default function DeckPage() {
  const params = useParams<{ id: string }>();
  return <DeckScreen source={{ kind: "deck", id: params.id }} />;
}
