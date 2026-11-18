"use client";

import Image from "next/image";
import { cn } from "@/lib/utils";
import { getInitial, getAvatarColor } from "@/lib/utils";

export function formatMessageTime(date: Date | string | null | undefined): string {
  if (!date) return "";
  const d = new Date(date);
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export interface GlassCardProps {
  className?: string;
  elevated?: boolean;
  children: React.ReactNode;
  onClick?: () => void;
}

export function GlassCard({ className, elevated = false, children, onClick }: GlassCardProps) {
  return (
    <div
      className={cn(
        "rounded-2xl border backdrop-blur-md shadow-sm",
        elevated
          ? "bg-white/90 border-white/92 shadow-lg dark:bg-gray-900/80 dark:border-gray-700/60 dark:shadow-xl"
          : "bg-white/76 border-white/92 shadow-md dark:bg-gray-900/70 dark:border-gray-700/80 dark:shadow-md",
        className
      )}
      onClick={onClick}
    >
      {children}
    </div>
  );
}

export interface UserAvatarProps {
  name: string;
  photoUrl?: string | null;
  size?: number;
  isOnline?: boolean;
  className?: string;
}

export function UserAvatar({
  name,
  photoUrl,
  size = 40,
  isOnline = false,
  className,
}: UserAvatarProps) {
  const initial = getInitial(name);
  const hue = getAvatarColor(name);

  return (
    <div
      className={cn("relative flex-shrink-0", className)}
      style={{ width: size, height: size }}
    >
      <div
        className={cn(
          "flex items-center justify-center rounded-full border-2 border-white/80 shadow-sm",
          photoUrl ? "" : `bg-gradient-to-br from-hsl(${hue},70%,60%) to-hsl(${(hue + 60) % 360},70%,60%)`
        )}
        style={{ width: size, height: size }}
      >
        {photoUrl ? (
          <Image
            src={photoUrl}
            alt={`${name} avatar`}
            width={size}
            height={size}
            unoptimized
            className="w-full h-full rounded-full object-cover"
          />
        ) : (
          <span className="text-white font-semibold text-sm">
            {initial}
          </span>
        )}
      </div>
      {isOnline && (
        <div className="absolute bottom-0 right-0 w-3 h-3 bg-emerald-500 border-2 border-white rounded-full z-10" />
      )}
    </div>
  );
}

export interface SearchBarProps {
  query: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  className?: string;
}

export function SearchBar({
  query,
  onChange,
  placeholder = "Search",
  className,
}: SearchBarProps) {
  return (
    <div className={cn("relative", className)}>
      <div className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
        </svg>
      </div>
      <input
        type="text"
        value={query}
        onChange={onChange}
        placeholder={placeholder}
        className={cn(
          "w-full pl-10 pr-4 py-2.5 rounded-full border border-white/60 bg-white/70 backdrop-blur-sm text-gray-900 placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-violet-500/50 focus:border-transparent transition-all",
          "dark:bg-gray-900/50 dark:text-white dark:border-gray-600/50 dark:placeholder-gray-400"
        )}
      />
    </div>
  );
}

export interface BadgeProps {
  count: number;
  className?: string;
}

export function Badge({ count, className }: BadgeProps) {
  return (
    <span
      className={cn(
        "flex -mx-1.5 min-w-[20px] items-center justify-center rounded-full px-2 py-0.5 text-xs font-bold leading-none text-white bg-violet-600 shadow-lg",
        className
      )}
    >
      {count > 99 ? "99+" : count}
    </span>
  );
}

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md" | "lg";
}

export function Button({
  variant = "primary",
  size = "md",
  className,
  children,
  ...props
}: ButtonProps) {
  const variants = {
    primary: "bg-violet-600 text-white hover:bg-violet-700 shadow-lg",
    secondary: "bg-white/60 dark:bg-gray-800/60 text-gray-900 dark:text-white hover:bg-white/80 dark:hover:bg-gray-700/80 border border-white/30 dark:border-gray-700",
    ghost: "text-gray-600 dark:text-gray-300 hover:bg-white/30 dark:hover:bg-gray-800",
    danger: "bg-rose-500 text-white hover:bg-rose-600",
  };

  const sizes = {
    sm: "px-3 py-1.5 text-sm",
    md: "px-4 py-2.5 text-base",
    lg: "px-6 py-3 text-lg",
  };

  return (
    <button
      className={cn(
        "rounded-xl font-medium transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center",
        variants[variant],
        sizes[size],
        className
      )}
      {...props}
    >
      {children}
    </button>
  );
}

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export function Input({ label, error, className, ...props }: InputProps) {
  return (
    <div className="w-full space-y-1.5">
      {label && (
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {label}
        </label>
      )}
      <input
        className={cn(
          "w-full px-4 py-3 rounded-xl border bg-white/50 dark:bg-gray-900/50 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-violet-500/50 focus:border-transparent transition-all",
          error ? "border-rose-500" : "border-white/30 dark:border-gray-700",
          className
        )}
        {...props}
      />
      {error && <p className="text-xs text-rose-500 dark:text-rose-400">{error}</p>}
    </div>
  );
}

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  rows?: number;
}

export function Textarea({ label, rows = 4, className, ...props }: TextareaProps) {
  return (
    <div className="w-full space-y-1.5">
      {label && (
        <label className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {label}
        </label>
      )}
      <textarea
        rows={rows}
        className={cn(
          "w-full px-4 py-3 rounded-xl border bg-white/50 dark:bg-gray-900/50 text-gray-900 dark:text-white placeholder-gray-500 dark:placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-violet-500/50 focus:border-transparent transition-all resize-none",
          "border-white/30 dark:border-gray-700",
          className
        )}
        {...props}
      />
    </div>
  );
}

export interface ToggleSwitchProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  className?: string;
}

export function ToggleSwitch({ checked, onChange, label, className }: ToggleSwitchProps) {
  return (
    <div className={cn("flex items-center justify-between gap-3", className)}>
      {label && <span className="text-sm text-gray-600 dark:text-gray-300">{label}</span>}
      <button
        onClick={() => onChange(!checked)}
        className={cn(
          "relative inline-block w-10 h-5 rounded-full transition-colors",
          checked ? "bg-violet-600" : "bg-gray-300 dark:bg-gray-700"
        )}
      >
        <span
          className={cn(
            "block w-3 h-3 rounded-full bg-white absolute transition-transform",
            checked ? "translate-x-4" : "translate-x-1"
          )}
        />
      </button>
    </div>
  );
}

export interface SectionHeaderProps {
  title: string;
  subtitle?: string;
  className?: string;
}

export function SectionHeader({ title, subtitle, className }: SectionHeaderProps) {
  return (
    <div className={cn("mb-6", className)}>
      <h2 className="text-lg font-bold text-gray-900 dark:text-white">{title}</h2>
      {subtitle && <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{subtitle}</p>}
    </div>
  );
}

export interface EmptyStateProps {
  icon: string;
  title: string;
  subtitle: string;
  className?: string;
}

export function EmptyState({ icon, title, subtitle, className }: EmptyStateProps) {
  return (
    <div className={cn("text-center py-20", className)}>
      <div className="w-20 h-20 rounded-full bg-white/40 dark:bg-gray-800/40 mx-auto mb-6 flex items-center justify-center">
        <span className="text-3xl">{icon}</span>
      </div>
      <h3 className="text-lg font-medium text-gray-900 dark:text-white">{title}</h3>
      <p className="text-gray-500 dark:text-gray-400 mt-2">{subtitle}</p>
    </div>
  );
}

export interface LoadingSpinnerProps {
  size?: "sm" | "md" | "lg";
  className?: string;
}

export function LoadingSpinner({ size = "md", className }: LoadingSpinnerProps) {
  const sizes = { sm: "h-6 w-6", md: "h-8 w-8", lg: "h-12 w-12" };
  return (
    <div
      className={cn("animate-spin rounded-full border-2 border-current", sizes[size], className)}
    />
  );
}

export interface DividerProps {
  text?: string;
  className?: string;
}

export function Divider({ text, className }: DividerProps) {
  return (
    <div className={cn("relative flex items-center my-6", className)}>
      <div className="flex-grow border-b border-white/20 dark:border-gray-700" />
      {text && (
        <span className="px-4 text-xs font-semibold text-gray-400 dark:text-gray-500 bg-white/30 dark:bg-gray-900/30">
          {text}
        </span>
      )}
      <div className="flex-grow border-b border-white/20 dark:border-gray-700" />
    </div>
  );
}

export interface ColorSwatchProps {
  value: string;
  onSelect: (value: string) => void;
  selected: boolean;
}

export function ColorSwatch({ value, onSelect, selected }: ColorSwatchProps) {
  return (
    <button
      onClick={() => onSelect(value)}
      className={cn(
        "w-8 h-8 rounded-full border-2 shadow-sm transition-all",
        selected
          ? "ring-2 ring-violet-500 scale-110"
          : "border-transparent hover:border-white/50 dark:hover:border-gray-600"
      )}
      style={{ backgroundColor: value }}
    />
  );
}
