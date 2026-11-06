// Kotlin's kotlin.random.Random(seed) (XorWowRandom) and MutableList.shuffle(random), bit for bit, so the website's
// deck simulations (ManaOdds, WinOdds) shuffle exactly like the app's and give the same numbers for the same cards.

export class KRandom {
  private x: number;
  private y: number;
  private z: number;
  private w: number;
  private v: number;
  private addend: number;

  /** Random(seed: Int): XorWowRandom(seed, seed shr 31). */
  constructor(seed: number) {
    const seed1 = seed | 0;
    const seed2 = seed1 >> 31;
    this.x = seed1;
    this.y = seed2;
    this.z = 0;
    this.w = 0;
    this.v = ~seed1;
    this.addend = (seed1 << 10) ^ (seed2 >>> 4);
    if ((this.x | this.y | this.z | this.w | this.v) === 0) throw new Error("Initial state must have at least one non-zero element.");
    for (let i = 0; i < 64; i++) this.nextInt();
  }

  nextInt(): number {
    let t = this.x;
    t = t ^ (t >>> 2);
    this.x = this.y;
    this.y = this.z;
    this.z = this.w;
    const v0 = this.v;
    this.w = v0;
    t = t ^ (t << 1) ^ v0 ^ (v0 << 4);
    this.v = t | 0;
    this.addend = (this.addend + 362437) | 0;
    return (t + this.addend) | 0;
  }

  private nextBits(bitCount: number): number {
    // takeUpperBits: this.ushr(32 - bitCount) and (-bitCount).shr(31); ushr by 32 is ushr by 0 on the JVM.
    const n = this.nextInt();
    const shift = (32 - bitCount) & 31;
    return ((n >>> shift) & (-bitCount >> 31)) | 0;
  }

  /** nextInt(until): 0 until [until], as Kotlin's Random.nextInt(from = 0, until). */
  nextIntUntil(until: number): number {
    const n = until | 0;
    if (n <= 0) throw new Error("bound must be positive");
    if ((n & -n) === n) {
      const bitCount = 31 - Math.clz32(n);
      return this.nextBits(bitCount);
    }
    let bits: number;
    let v: number;
    do {
      bits = this.nextInt() >>> 1;
      v = bits % n;
    } while (((bits - v + (n - 1)) | 0) < 0);
    return v;
  }

  /** nextDouble(): Kotlin's doubleFromParts(nextBits(26), nextBits(27)). */
  nextDouble(): number {
    const hi = this.nextBits(26);
    const lo = this.nextBits(27);
    return (hi * 134217728 + lo) / 9007199254740992;
  }
}

/** MutableList.shuffle(random), in place. */
export function shuffleInPlace<T>(list: T[], random: KRandom): T[] {
  for (let i = list.length - 1; i >= 1; i--) {
    const j = random.nextIntUntil(i + 1);
    const t = list[i];
    list[i] = list[j];
    list[j] = t;
  }
  return list;
}

/** Iterable.shuffled(random): a shuffled copy. */
export function shuffled<T>(list: readonly T[], random: KRandom): T[] {
  return shuffleInPlace([...list], random);
}
