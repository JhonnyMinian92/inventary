# Inventary Web — React

Frontend SPA for the Inventary inventory backend. Built with React, Vite and Lucide icons; UI components call REST through the shared client in `src/api.js`.

## Requirements

- Node.js 20+
- Backend reachable from the browser
- A user authorized by the backend

## Local development

```bash
cd app-inventary-ecuapp
cp .env.example .env
npm install
npm run dev
```

Set `VITE_API_BASE_URL` to the backend base URL including `/api/v1`. Default: `http://localhost:8080/api/v1`. Configure backend CORS to allow the Vite origin (normally `http://localhost:5173`).

## Build

```bash
npm run build
npm run preview
```

## Modules

- Overview dashboard with live API data.
- Product catalogue: create/edit products, categories, units and dynamic JSON properties.
- Purchase orders and transactional sales.
- Stock balances and movement history.
- Inventory balance report by warehouse, product and date range.
- Bearer token authentication, centralized error handling and idempotency keys for stock-changing commands.

## API integration

Uses backend routes under `/api/v1`, including `/catalog/products`, `/catalog/categories`, `/inventory/balances`, `/inventory/movements`, `/purchase-orders`, `/sales` and `/reports/inventory-balance`. Stock is never edited directly in the browser. A purchase order alone does not add stock: the backend goods-receipt posting workflow must be completed to record physical receipt.

The UI surfaces API validation/authentication errors rather than simulating successful data. Collection adapters accept common envelopes (`items`, `content`, `data`, `results`, `records`). The backend's OpenAPI schema remains authoritative for request DTOs.
