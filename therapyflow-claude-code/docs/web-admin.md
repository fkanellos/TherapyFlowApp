# Web Admin Dashboard

## Overview

The Web Admin Dashboard is a browser-based interface for the OWNER role.
It calls the exact same Ktor REST API as the mobile app — no separate backend.

**Tech stack:**
- HTML5 + CSS3 (Flexbox/Grid)
- Vanilla JavaScript (ES6+)
- Fetch API for REST calls (same pattern as AJAX)
- Chart.js for analytics charts
- No framework — keep it simple and maintainable

**Why vanilla JS:**
Filippos knows HTML/CSS/JS from his master's degree.
He can read, review and guide the implementation.
The dashboard is tables, forms, charts — no complex UI needed.

## Access

```
URL: https://admin.therapyflow.io
     (or: https://app.therapyflow.io/admin)
Auth: Same JWT as mobile — OWNER role required
      Redirect to login if no valid token
```

## Pages / Screens

### 1. Dashboard (Home)
```
Overview stats:
  - Total therapists (active)
  - Today's appointments (all therapists)
  - This month's revenue
  - Pending payroll periods

Quick links:
  - Calculate this month's payroll
  - Add new therapist
  - View all appointments
```

### 2. Therapists Management
```
List view:
  - Name, specialization, commission rate
  - Active/inactive toggle
  - Link to their appointments + earnings

Add/Edit therapist form:
  - firstName, lastName, specialization
  - commissionRate (%)
  - receivesSupervisionFee (checkbox)
  - Link to user account (email, role)
```

### 3. Clients Management
```
List view:
  - Name, linked therapist, custom price
  - Total sessions, total spent
  - Pending charges (late cancellations)

Add/Edit client:
  - firstName, lastName
  - primaryTherapistId
  - customPrice (optional — overrides default)
  - googleCalendarName (for sync matching)
  - Aliases (e.g. "Γιώτα" → maps to this client)
```

### 4. Appointments (All)
```
Calendar or list view:
  - Filter by: therapist, date range, status
  - Color coding matching existing Google Calendar:
      RED  = CANCELLED_LATE (must pay next visit)
      GREY = CANCELLED_EARLY (no charge)
      GREEN = COMPLETED
      BLUE = SCHEDULED

  - Click appointment → edit status, notes, price
  - Manual override for edge cases
```

### 5. Payroll
```
List of periods (month/year, status):
  - DRAFT → can recalculate
  - FINALIZED → locked, export only

Calculate payroll:
  - Select month/year → POST /payroll/calculate
  - Review breakdown per therapist
  - Check pending charges (late cancellations)
  - Finalize → PUT /payroll/{id}/finalize

Export:
  - PDF → GET /payroll/{id}/export?format=pdf
  - Excel → GET /payroll/{id}/export?format=excel
```

### 6. Analytics
```
Charts (Chart.js):
  - Revenue per month (line chart)
  - Sessions per therapist (bar chart)
  - Cancellation rate (pie chart)
  - Top clients by revenue

Date range filter: this month, last 3 months, year, custom
Export: PDF report, CSV data
```

### 7. Settings
```
Workspace:
  - Name, logo upload
  - Brand colors (primary, secondary)

Google Calendar:
  - OAuth connection status
  - Sync settings

Feature flags:
  - Toggle features per workspace plan
```

## API Integration Pattern

```javascript
// Base setup — store token in memory (not localStorage)
const api = {
    baseUrl: 'https://api.therapyflow.io/v1',
    token: null,

    async fetch(endpoint, options = {}) {
        const response = await fetch(`${this.baseUrl}${endpoint}`, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${this.token}`,
                ...options.headers
            }
        });

        if (response.status === 401) {
            // Token expired — redirect to login
            window.location.href = '/login';
            return;
        }

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || 'API Error');
        }

        return response.json();
    },

    get: (endpoint) => api.fetch(endpoint),
    post: (endpoint, body) => api.fetch(endpoint, {
        method: 'POST',
        body: JSON.stringify(body)
    }),
    put: (endpoint, body) => api.fetch(endpoint, {
        method: 'PUT',
        body: JSON.stringify(body)
    }),
    delete: (endpoint) => api.fetch(endpoint, { method: 'DELETE' })
};

// Usage example
async function loadTherapists() {
    try {
        showLoading();
        const { data } = await api.get('/therapists');
        renderTherapistTable(data);
    } catch (error) {
        showError(error.message);
    } finally {
        hideLoading();
    }
}
```

## File Structure

```
web-admin/
├── index.html              ← login page
├── dashboard.html          ← main dashboard
├── therapists.html
├── clients.html
├── appointments.html
├── payroll.html
├── analytics.html
├── settings.html
├── css/
│   ├── main.css            ← base styles, typography, colors
│   ├── components.css      ← reusable components (cards, tables, forms)
│   └── charts.css
├── js/
│   ├── api.js              ← Fetch API wrapper (above)
│   ├── auth.js             ← login, token management
│   ├── components.js       ← reusable UI (modals, toasts, loading)
│   ├── therapists.js
│   ├── appointments.js
│   ├── payroll.js
│   └── analytics.js
└── assets/
    └── logo.webp
```

## Design Guidelines

Match the TherapyFlow brand:
```css
:root {
    --primary: #1976D2;        /* therapist blue */
    --admin: #673AB7;          /* admin purple */
    --success: #2E7D32;
    --cancel-late: #C62828;    /* RED - late cancellation */
    --cancel-early: #9E9E9E;   /* GREY - early cancellation */
    --pending: #E65100;        /* ORANGE - pending charge */
    --bg: #F5F5F5;
    --surface: #FFFFFF;
}
```

## Security Notes

- JWT token stored in **memory only** (not localStorage, not cookies)
- Token refreshed automatically before expiry
- All routes protected — redirect to login if no token
- OWNER role enforced by API — web UI just reflects what API returns
- Never display raw error messages from API to user
