export declare function winOddsJson(cardsJson: string, identity: string, saltCard: string | null, games?: number | null, onProgress?: ((started: number) => void) | null): string;
export declare function winBatchJson(cardsJson: string, identity: string, saltCard: string | null, batch: number, games?: number | null, snapshotAt?: number | null, onProgress?: ((started: number) => void) | null, onSnapshot?: ((tallyJson: string) => void) | null): string;
export declare function winMergeJson(cardsJson: string, identity: string, saltCard: string | null, talliesJson: string): string;
export declare function saltCardJson(body: string): string | null;
