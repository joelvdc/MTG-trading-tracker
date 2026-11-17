"use client";

import { useParams } from "next/navigation";
import { DeckScreen } from "@/components/DeckScreen";

/** One Commander precon from MTGJSON, shown like a deck but as a preview ([file] is the deck list's file name). */
export default function PreconPage() {
  const params = useParams<{ file: string }>();
  return <DeckScreen source={{ kind: "precon", file: decodeURIComponent(params.file) }} />;
}
