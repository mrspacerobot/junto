/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#f4f8e8',
          100: '#e6f0cc',
          200: '#cde199',
          300: '#b0cf66',
          400: '#94bf3d',
          500: '#6b8e23',
          600: '#5a7a1c',
          700: '#4a6516',
          800: '#3a5010',
          900: '#2a3b0b',
          950: '#1a2606',
        },
        warm: {
          50: '#FAF8F4',
          100: '#F7F4EF',
          200: '#EFECE6',
          300: '#E4E1DA',
          400: '#D0CDC6',
        },
        accent: {
          DEFAULT: '#4A7230',
          bg: '#EDF1E6',
          hover: '#3D6128',
        },
      },
      fontFamily: {
        display: ['Playfair Display', 'serif'],
        sans: ['DM Sans', 'sans-serif'],
      },
      boxShadow: {
        'card': '0 1px 4px rgba(0,0,0,0.04)',
        'card-hover': '0 4px 20px rgba(0,0,0,0.08)',
      },
    },
  },
  plugins: [],
}
