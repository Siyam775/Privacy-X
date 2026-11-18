/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: "class",
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        lavender: {
          mist: "#EDE7FF",
          cloud: "#F6EEFF",
          glow: "#F3EEFF",
        },
        plum: {
          ink: "#1D1633",
          grey: "#5D5475",
        },
        violet: {
          royal: "#7C3AED",
          electric: "#9B6BFF",
          midnight: "#120E24",
        },
        pink: {
          orchid: "#EC4899",
          blush: "#FBE9F7",
          soft: "#D9B8FF",
        },
        mint: {
          whisper: "#E7F6F5",
        },
        ios: {
          ice: "#EBF2FF",
          crystal: "#5B7FFF",
          crystalViolet: "#8E63FF",
          darkTop: "#0A0D18",
          darkMid: "#15122B",
          darkBottom: "#0C1A26",
        },
        charcoal: "#141416",
        offWhite: "#F6F6F7",
        neutral: {
          hairlineLight: "#DADADF",
          hairlineDark: "#2C2C32",
          inkLight: "#52525B",
          inkDark: "#A1A1AA",
        },
      },
      animation: {
        "pulse-slow": "pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite",
        "orbit": "orbit 20s linear infinite",
      },
      keyframes: {
        orbit: {
          "0%": { transform: "rotate(0deg)" },
          "100%": { transform: "rotate(360deg)" },
        },
      },
    },
  },
  plugins: [],
};
