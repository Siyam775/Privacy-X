"use client";

import Link from "next/link";
import { ArrowRight, BellRing, Camera, Clock3, LockKeyhole, MessageCircle, Phone, Sparkles, UsersRound } from "lucide-react";

const features = [
  { icon: MessageCircle, title: "Private conversations", text: "One-to-one chats, groups, and channels in one calm, organized inbox." },
  { icon: Phone, title: "Voice and video calls", text: "Start a call from a conversation and keep the people you care about close." },
  { icon: Camera, title: "Stories that disappear", text: "Share a quick moment with friends, with audience controls built in." },
  { icon: UsersRound, title: "Communities", text: "Bring people together around shared interests, with useful admin tools." },
  { icon: Sparkles, title: "Built-in AI assistant", text: "Get help with ideas and questions without leaving your messaging space." },
  { icon: Clock3, title: "More control", text: "Choose your appearance and manage privacy and notification preferences." },
];

export function PublicLanding() {
  return (
    <main className="min-h-dvh overflow-hidden bg-[var(--px-bg)] text-[var(--px-text-primary)]">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-[620px] bg-gradient-to-b from-violet-500/15 via-pink-500/10 to-transparent" />
      <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between gap-2 px-3 py-4 min-[380px]:px-5 sm:px-8 sm:py-5">
        <a href="#top" className="flex items-center gap-2.5 font-bold tracking-tight">
          <span className="flex h-9 w-9 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-600 to-pink-500 text-white shadow-lg sm:h-10 sm:w-10"><LockKeyhole size={19} /></span>
          <span className="text-base min-[380px]:text-lg">PrivacyX</span>
        </a>
        <nav className="flex items-center gap-4 sm:gap-7" aria-label="Main navigation">
          <a href="#features" className="hidden text-sm font-medium text-[var(--px-text-secondary)] transition hover:text-violet-600 sm:inline">Features</a>
          <a href="#privacy" className="hidden text-sm font-medium text-[var(--px-text-secondary)] transition hover:text-violet-600 sm:inline">Privacy</a>
          <Link href="/signup" className="shrink-0 rounded-full bg-violet-600 px-3 py-2.5 text-xs font-semibold text-white shadow-md transition hover:bg-violet-700 min-[380px]:px-4 min-[380px]:text-sm">Create account</Link>
        </nav>
      </header>

      <section id="top" className="relative z-10 mx-auto grid min-h-0 w-full max-w-6xl items-center gap-10 px-4 pb-14 pt-8 sm:min-h-[560px] sm:gap-12 sm:px-8 sm:pb-16 sm:pt-12 lg:grid-cols-[1.08fr_.92fr] lg:pb-24 lg:pt-16">
        <div>
          <div className="mb-5 inline-flex items-center gap-2 rounded-full border border-violet-500/20 bg-white/60 px-3.5 py-2 text-sm font-medium text-violet-700 shadow-sm backdrop-blur dark:bg-white/5 dark:text-violet-200"><LockKeyhole size={15} /> Your conversations, in one place</div>
          <h1 className="max-w-2xl text-4xl font-bold leading-[1.08] tracking-tight min-[380px]:text-5xl sm:text-6xl lg:text-7xl">Stay close.<br /><span className="bg-gradient-to-r from-violet-600 to-pink-500 bg-clip-text text-transparent">Keep control.</span></h1>
          <p className="mt-6 max-w-xl text-lg leading-8 text-[var(--px-text-secondary)]">Private chats, calls, stories, and communities come together in one thoughtfully designed space.</p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link href="/signup" className="inline-flex items-center gap-2 rounded-full bg-violet-600 px-6 py-3.5 font-semibold text-white shadow-lg transition hover:-translate-y-0.5 hover:bg-violet-700">Get started <ArrowRight size={18} /></Link>
            <a href="#features" className="rounded-full border border-[var(--px-border)] bg-white/60 px-6 py-3.5 font-semibold transition hover:bg-white/90 dark:bg-white/5 dark:hover:bg-white/10">Explore features</a>
          </div>
          <p className="mt-5 text-sm text-[var(--px-text-muted)]">See what PrivacyX offers before creating an account.</p>
        </div>

        <div className="relative mx-auto w-full max-w-md">
          <div className="absolute -inset-5 rounded-[38px] bg-gradient-to-br from-violet-500/25 to-pink-500/25 blur-2xl" />
          <div className="relative rounded-[32px] border border-white/80 bg-white/75 p-4 shadow-2xl backdrop-blur-2xl dark:border-white/10 dark:bg-gray-900/70 sm:p-5">
            <div className="flex items-center justify-between px-2 pb-4 pt-1">
              <div><p className="text-xs font-medium text-[var(--px-text-muted)]">YOUR SPACE</p><h2 className="mt-1 text-xl font-bold">Chats</h2></div>
              <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br from-violet-500 to-pink-500 text-white"><MessageCircle size={21} /></div>
            </div>
            <div className="mb-4 rounded-2xl border border-violet-500/10 bg-violet-500/5 px-4 py-3 text-sm text-[var(--px-text-muted)]">Search people and conversations</div>
            <div className="space-y-2.5">
              {[
                { initials: "AM", name: "Alex Morgan", message: "That sounds perfect!", color: "from-violet-500 to-indigo-400", time: "2m" },
                { initials: "WK", name: "Weekend crew", message: "Sam: Shared a photo", color: "from-pink-500 to-orange-400", time: "18m" },
                { initials: "DS", name: "Design space", message: "New topic: Inspiration", color: "from-cyan-500 to-blue-500", time: "1h" },
              ].map((chat) => <div key={chat.name} className="flex items-center gap-3 rounded-2xl border border-[var(--px-border)] bg-white/65 p-3 dark:bg-white/5">
                <div className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br ${chat.color} text-sm font-bold text-white`}>{chat.initials}</div>
                <div className="min-w-0 flex-1"><div className="flex justify-between gap-2"><p className="truncate font-semibold">{chat.name}</p><span className="text-xs text-[var(--px-text-muted)]">{chat.time}</span></div><p className="mt-1 truncate text-sm text-[var(--px-text-secondary)]">{chat.message}</p></div>
              </div>)}
            </div>
            <div className="mt-4 flex items-center justify-between rounded-2xl bg-gray-950 px-4 py-3 text-white shadow-lg dark:bg-white/10">
              <div className="flex items-center gap-2 text-sm font-medium"><Sparkles size={16} className="text-pink-300" /> Your space, your style</div>
              <div className="flex -space-x-1"><span className="h-3 w-3 rounded-full bg-violet-400" /><span className="h-3 w-3 rounded-full bg-pink-400" /><span className="h-3 w-3 rounded-full bg-cyan-300" /></div>
            </div>
          </div>
        </div>
      </section>

      <section id="features" className="relative z-10 border-y border-[var(--px-border)] bg-white/35 py-20 backdrop-blur-sm dark:bg-white/[0.02]">
        <div className="mx-auto w-full max-w-6xl px-5 sm:px-8">
          <div className="max-w-2xl"><p className="text-sm font-bold uppercase tracking-[.18em] text-violet-600">Made for everyday connection</p><h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">Everything you need to stay connected</h2><p className="mt-4 leading-7 text-[var(--px-text-secondary)]">Take a look around. You can explore the features first and create your account whenever you’re ready.</p></div>
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {features.map(({ icon: Icon, title, text }) => <article key={title} className="rounded-3xl border border-[var(--px-border)] bg-white/65 p-6 shadow-sm backdrop-blur-xl transition hover:-translate-y-1 hover:shadow-lg dark:bg-white/[0.04]">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-violet-500/10 text-violet-600 dark:text-violet-300"><Icon size={22} /></div>
              <h3 className="mt-5 text-lg font-semibold">{title}</h3><p className="mt-2 leading-6 text-[var(--px-text-secondary)]">{text}</p>
            </article>)}
          </div>
        </div>
      </section>

      <section id="privacy" className="relative z-10 mx-auto grid w-full max-w-6xl gap-8 px-5 py-20 sm:px-8 md:grid-cols-2 md:items-center">
        <div><div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-500/10 text-emerald-600"><LockKeyhole size={23} /></div><h2 className="mt-5 text-3xl font-bold tracking-tight">Your account. Your choices.</h2><p className="mt-4 leading-7 text-[var(--px-text-secondary)]">Set your profile, control how you appear to others, adjust notifications, and choose a look that feels right to you.</p></div>
        <div className="space-y-3">
          {[{ icon: LockKeyhole, title: "Privacy settings", text: "Manage profile visibility and who can reach you." }, { icon: BellRing, title: "Notification controls", text: "Choose when and how PrivacyX gets your attention." }, { icon: Sparkles, title: "A space that feels like yours", text: "Personalize the theme, color, and motion settings." }].map(({ icon: Icon, title, text }) => <div key={title} className="flex gap-4 rounded-2xl border border-[var(--px-border)] bg-white/55 p-4 dark:bg-white/[0.04]"><div className="mt-0.5 text-violet-600"><Icon size={20} /></div><div><p className="font-semibold">{title}</p><p className="mt-1 text-sm text-[var(--px-text-secondary)]">{text}</p></div></div>)}
        </div>
      </section>

      <footer className="relative z-10 border-t border-[var(--px-border)] bg-white/35 dark:bg-white/[0.02]">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-5 py-8 sm:flex-row sm:items-center sm:justify-between sm:px-8"><p className="text-sm text-[var(--px-text-muted)]">PrivacyX · Messaging made for people</p><Link href="/signup" className="inline-flex items-center gap-2 font-semibold text-violet-600 hover:text-violet-700">Create your account <ArrowRight size={16} /></Link></div>
      </footer>
    </main>
  );
}
