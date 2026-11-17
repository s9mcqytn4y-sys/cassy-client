// Order notice sounds synthesised with Web Audio (PRD v3 Q7): no audio files to ship or license.

export type BellKind = "paid" | "ready" | "delivered" | "expiringSoon";

/** [frequency Hz, start s, length s]: distinct acoustic chimes per order milestone. */
export function bellNotes(kind: BellKind): [number, number, number][] {
  switch (kind) {
    case "paid":
      return [
        [523.25, 0, 0.22],
        [659.25, 0.11, 0.24],
        [783.99, 0.22, 0.42]
      ];
    case "ready":
      return [
        [880, 0, 0.35],
        [1174.66, 0.18, 0.5]
      ];
    case "delivered":
      return [
        [659.25, 0, 0.28],
        [783.99, 0.14, 0.42]
      ];
    case "expiringSoon":
      return [
        [587.33, 0, 0.18],
        [587.33, 0.24, 0.22]
      ];
  }
}

let ctx: AudioContext | null = null;

/** Browsers only allow sound after a tap; call this from a tap so a later bell can play. */
export function primeBell() {
  if (typeof window === "undefined" || ctx) return;
  const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (Ctor) ctx = new Ctor();
}

export async function playBell(kind: BellKind) {
  if (!ctx) return;
  if (ctx.state === "suspended") {
    try {
      await ctx.resume();
    } catch {
      return;
    }
  }
  const now = ctx.currentTime;
  for (const [freq, start, length] of bellNotes(kind)) {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = "sine";
    osc.frequency.value = freq;
    // Quick attack, exponential decay: a bell, not a beep.
    gain.gain.setValueAtTime(0.0001, now + start);
    gain.gain.exponentialRampToValueAtTime(0.25, now + start + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, now + start + length);
    osc.connect(gain).connect(ctx.destination);
    osc.start(now + start);
    osc.stop(now + start + length + 0.05);
  }
}

const MUTE_KEY = "cassy_sound_off";

export function readMuted(): boolean {
  try {
    return localStorage.getItem(MUTE_KEY) === "1";
  } catch {
    return false;
  }
}

export function writeMuted(muted: boolean) {
  try {
    if (muted) localStorage.setItem(MUTE_KEY, "1");
    else localStorage.removeItem(MUTE_KEY);
  } catch {
    // Without storage the choice lasts until the page closes.
  }
}
