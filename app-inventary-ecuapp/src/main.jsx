import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import { migrateLegacyUrl } from './router.js';
import './styles.css';
// Los enlaces antiguos (#/products/new) se convierten en rutas reales antes de montar la aplicación.
migrateLegacyUrl();
createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
