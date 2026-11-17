import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
export default defineConfig({ plugins: [react(),tailwindcss()], server: {proxy:{'/api':(process.env.SMARTROUTE_API_URL || 'http://127.0.0.1:8080'),'/actuator':(process.env.SMARTROUTE_API_URL || 'http://127.0.0.1:8080')}} });

