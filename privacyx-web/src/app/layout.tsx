import type { Metadata } from "next";
import { AppShell } from "@/components/AppShell";
import "./globals.css";

export const metadata: Metadata = {
  title: "PrivacyX",
  description: "Private chats, voice and video calls, stories, and communities.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="h-full">
      <body className="min-h-[100dvh] flex flex-col bg-white dark:bg-gray-900 theme-transition antialiased selection:bg-violet-500/30">
        <AppShell>{children}</AppShell>
      </body>
    </html>
  );
}
