# FinSight AI — PayPal AI Hackathon 2026: 3-Minute Demo Script

**Total Target Runtime**: 2 minutes 45 seconds (Under the 3-minute hard ceiling)  
**Presenter**: Solo Full-Stack Engineer / Architect  
**Resolution**: 1080p (1920x1080) at 60fps  

---

## Timeline & Scene-by-Scene Breakdown

```
[00:00 - 00:25] Hook & Problem Statement (Landing Screen)
[00:25 - 00:55] Authentication & Security Architecture (Sign-in / Profile)
[00:55 - 01:40] PayPal Sandbox Integration & Live Payment (PayPal Hub)
[01:40 - 02:20] Grounded AI Financial Assistant & Bridge (AI Assistant)
[02:20 - 02:45] Financial Health, Budgets & Wrap-Up (Dashboard)
```

---

### Scene 1: Hook & Problem Statement (00:00 – 00:25)
* **Screen View**: FinSight AI Landing Page (`http://localhost:5173/`)
* **Visual Action**: Cursor scrolls past the real-time net worth metric card, cash flow chart, and feature badges.
* **Spoken Voiceover**:
  > *"Every month, millions of digital consumers and freelance professionals make dozens of payments across PayPal and bank accounts—yet traditional financial apps either guess numbers with hallucinating AI or fail to connect payment APIs with genuine intelligence.*  
  > *Meet **FinSight AI**: a privacy-first, grounded financial intelligence platform engineered on Spring Boot, PostgreSQL, and React that connects directly to the PayPal Sandbox to analyze real payments with zero guesswork."*

---

### Scene 2: Authentication & Security Architecture (00:25 – 00:55)
* **Screen View**: Topbar `[Sign In]` button $\rightarrow$ Auth Screen $\rightarrow$ Topbar Avatar Menu $\rightarrow$ Profile View
* **Visual Action**:
  1. Click `Sign In` in top navigation.
  2. Enter credentials (`alex@example.com` / password) and submit.
  3. Point to the topbar avatar displaying "AS", click to reveal the dropdown menu (`Profile`, `Settings`, `Sign out`).
  4. Click `Profile` to highlight the AES-256-GCM encryption badge and session security.
* **Spoken Voiceover**:
  > *"FinSight uses enterprise-grade security: BCrypt hashed credentials, Spring Security CSRF tokens, HttpOnly session cookies, and AES-256-GCM authenticated encryption for all external credentials and tokens at rest. Every single query is strictly scoped to the authenticated user ID."*

---

### Scene 3: PayPal Sandbox Integration & Live Payment (00:55 – 01:40)
* **Screen View**: Left Sidebar $\rightarrow$ `PayPal Sandbox` (`/paypal`)
* **Visual Action**:
  1. Show the **Connection Status** card confirming live Sandbox reporting connectivity (`api-m.sandbox.paypal.com`).
  2. Scroll to the **PayPal Sandbox Checkout Simulator**.
  3. Enter `$50.00`, Category: `Software`, Description: `Cloud Server Hosting`.
  4. Click `[Execute Sandbox Checkout]`.
  5. The card resolves displaying the real PayPal Order ID (e.g. `1RY72918A6368931J`), status `CREATED`, and the live PayPal approval link (`checkoutnow?token=...`).
  6. Explain that FinSight connects directly to PayPal's v2 Orders API. Once approved by a Sandbox buyer, the capture settlement is automatically ingested into the PostgreSQL ledger with provider `PAYPAL`.
  7. Click `[Analyze with AI Assistant]` to inspect PayPal spending metrics.
* **Spoken Voiceover**:
  > *"Here in the PayPal Hub, FinSight AI connects directly to PayPal's official Sandbox APIs. We implement both historical Transaction Search reporting and the real-time PayPal v2 Checkout Orders API.*  
  > *Watch as we create a live order with intent CAPTURE: PayPal immediately returns a real sandbox order token and checkout approval URL. Once approved, our capture endpoint ingests the settlement directly into PostgreSQL.*  
  > *Now let's ask our AI Assistant to analyze our PayPal financial patterns with zero guesswork."*

---

### Scene 4: Grounded AI Financial Assistant & Bridge (01:40 – 02:20)
* **Screen View**: AI Assistant View (`/assistant`)
* **Visual Action**:
  1. Show the prefilled prompt: *"Show my PayPal activity and spending"*.
  2. Press Enter to submit.
  3. Point to the structured response:
     - Connected Sandbox status pill
     - Total PayPal transaction volume & breakdown
     - Recurring PayPal subscriptions detected
     - Top merchants paid via PayPal
     - Estimated processing fees
  4. Click a suggested question chip: *"What are my highest expense categories?"*
* **Spoken Voiceover**:
  > *"Notice how the FinSight AI Assistant responds. Unlike generic LLM wrappers that fabricate numbers, FinSight's intelligence engine is 100% mathematically grounded in the user's actual database records.*  
  > *It recognized our freshly captured PayPal transaction, updated total spending, identified recurring subscription patterns, and surfaced actionable savings recommendations."*

---

### Scene 5: Financial Health, Budgets & Wrap-Up (02:20 – 02:45)
* **Screen View**: Left Sidebar $\rightarrow$ `Financial Health` (`/financial-health`) $\rightarrow$ `Dashboard` (`/`)
* **Visual Action**:
  1. Show the **Financial Health Score** (e.g. 82/100) with explainable component weights.
  2. Return to the main Dashboard to show cash flow and budget alerts.
  3. Click Avatar $\rightarrow$ `Sign Out` to show clean session termination and guest redirect.
* **Spoken Voiceover**:
  > *"FinSight AI scores complete financial resilience across 5 deterministic dimensions, manages category budgets, and keeps sensitive data private on your own infrastructure.*  
  > *With 37 backend tests, 38 frontend tests, clean Docker containerization, and full PayPal Sandbox compliance, FinSight AI is production-ready for the PayPal AI Hackathon 2026. Thank you!"*

---

## Checklist for Video Recording
- [ ] Browser set to 100% zoom at 1080p resolution.
- [ ] Backend running (`.\mvnw.cmd spring-boot:run`) on `http://localhost:8081`.
- [ ] Frontend running (`npm run dev`) on `http://localhost:5173`.
- [ ] Audio clean and clear with microphone noise suppression enabled.
- [ ] Video exported under 3 minutes (e.g. 2m 40s) in MP4 format.

