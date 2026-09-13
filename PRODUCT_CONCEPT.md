# Coffeetamine — Concept Document

## 1. Vision

Coffeetamine is a location-based social discovery platform focused on spontaneous, meaningful real-world conversations between people who share interests and a love for coffee.

The core idea is not dating or messaging, but **vibe-based micro-encounters** in physical space.

Users express their current emotional state and availability, and the system helps them discover nearby people with similar interests and compatible “vibes”.

---

## 2. Core Pillars

- **Presence-based social discovery**
- **Mood-driven self-expression**
- **Interest overlap matching**
- **Low-friction interaction (no chat)**
- **Real-world coffee-context anchoring**
- **Opt-in visibility model**

---

## 3. Core Loop

1. User logs in via Google
2. Completes onboarding wizard
3. Sets status:
   - Ready / Not Ready
   - optional mood/comment
4. Appears on map (if Ready)
5. Sees nearby users (filtered)
6. Opens user mood board + profile
7. Sends **ping**
8. Receives ping from others
9. Sends **pong**
10. Mutual ping → match event

---

## 4. Onboarding Flow

### Wizard (mandatory after login)

Step 1: Basic Info
- Name / nickname
- Avatar (Google or upload)

Step 2: Interests
- Selection from predefined tag list
- User selects X interests

Step 3: About (optional)
- Short free-text bio

Completion → system entry

---

## 5. User Model

### Core attributes
- UserId
- Name
- Avatar
- Interests (tag IDs)
- About text
- Status (Ready / Not Ready)
- Mood comment (optional)
- Location (approximate / offset-based)
- Visibility state

---

## 6. Interests System

- Fixed global tag catalog
- User selects subset of tags
- No free-form tags in MVP

Used for:
- filtering
- compatibility scoring
- ranking nearby users

---

## 7. Matching System

### Compatibility Score (MVP)

- Simple overlap of interest tags
- Result normalized to **1–100**
- Used as filter + ranking signal

### Discovery Rules

User appears only if:
- Status = Ready
- Distance within radius (with offset noise)
- Compatibility score ≥ threshold (X%)
- Geo + interests combined filter

---

## 8. Map & Discovery UI

### Main interface:
- Map with user pins (primary layer)

### Interaction:
- Tap pin → open mood board + profile
- Mood board includes:
  - 6 images (Unsplash-based)
  - mood comment
  - interests preview

---

## 9. Mood Board System

### MVP implementation:
- User selects 6 images from Unsplash
- Manual composition by user
- Represents current emotional / contextual state

Future:
- AI-generated mood visuals

---

## 10. Location Model

- Location is intentionally imprecise
- Offset-based radius system
- User may optionally specify more precise description in status text

### Coffee context:
- Coffee spots are soft anchors (POI layer)
- Includes:
  - cafes
  - street coffee stands
  - informal public seating areas

Users may:
- be near POI
- associate themselves with POI via status text

---

## 11. Interaction Model

### Ping System

- User A sends ping
- User B receives notification
- B can view A profile
- B responds with pong

### Match condition:
- Mutual ping (A ↔ B)

No chat exists in MVP.

Post-match state is undefined (future expansion).

---

## 12. Status System

- Binary state:
  - Ready
  - Not Ready

- Optional text comment:
  - mood / context / vibe

Rules:
- Not Ready → hidden from discovery
- Ready → eligible for map visibility and matching

---

## 13. Coffee Layer

Coffee is a contextual anchor, not a strict venue system.

- Used as thematic identity layer
- Enhances mood expression
- Supports real-world anchoring
- Not required for interaction

---

## 14. UX Philosophy

- Minimal cognitive load
- No infinite messaging
- No swipe gamification
- Intent-based interactions only
- Real-world grounded discovery
- Opt-in visibility

---

## 15. Future Extensions

- AI-generated mood boards
- Advanced similarity (embeddings)
- Real-time coffee spot clustering
- Event-based group meetups
- Chat unlocked after match
- Reputation / trust system
- Temporal presence (“I’m here for 30 min”)
