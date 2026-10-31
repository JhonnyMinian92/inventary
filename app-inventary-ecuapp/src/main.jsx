import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './app/App.jsx';
import { migrateLegacyUrl } from './app/router/index.js';
import './app/styles/index.css';

migrateLegacyUrl();
createRoot(document.getElementById('root')).render(<React.StrictMode><App /></React.StrictMode>);
