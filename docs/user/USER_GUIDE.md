# User Guide
## Award Monitoring & Tracking System

> **Phase 17 Deliverable**: Documentation & Knowledge Management  
> **Document Version**: 1.0  
> **Last Updated**: January 2026  
> **Author**: Stefan Kostyk  

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Submitting Awards](#submitting-awards)
3. [Document Management](#document-management)
4. [Tracking Award Status](#tracking-award-status)
5. [Reviewing Awards](#reviewing-awards)
6. [Achievements and Visibility](#achievements-and-visibility)
7. [My dashboard](#my-dashboard)
8. [Managing Your Profile](#managing-your-profile)
9. [Notifications](#notifications)
10. [FAQ](#faq)

---

## Getting Started

### First-Time Login

1. Navigate to `https://award-system.edu.ua`
2. Click **"Login with University Account"**
3. Enter your university email and password
4. Complete two-factor authentication (if enabled)
5. Accept the Terms of Service and Privacy Policy

### Dashboard Overview

After login, the navigation leads to your awards (**Мої нагороди**), your statistics (**Мій дашборд**, see
[My dashboard](#my-dashboard)) and the achievements shared with colleagues (**Досягнення**).

---

## Submitting Awards

### Step 1: Start New Submission

1. Click **"+ New Award"** button
2. Select the award category:
   - International (conferences, publications)
   - National (competitions, grants)
   - Regional (local achievements)
   - Local (university-level)

### Step 2: Fill Award Details

| Field | Description | Required |
|-------|-------------|----------|
| Title | Award name exactly as on certificate | Yes |
| Description | Brief description of achievement | No |
| Award Date | Date received (YYYY-MM-DD) | Yes |
| Issuing Organization | Who granted the award | Yes |
| Category | Select from dropdown | Yes |

### Step 3: Upload Supporting Documents

**Required Documents:**
- Certificate or diploma (PDF, high quality scan)

**Optional Documents:**
- Event program
- Publication excerpt
- Photos
- Additional supporting materials

**File Requirements:**
- Formats: PDF, JPEG, PNG
- Maximum size: 10MB per file
- Recommended: PDF at 300 DPI

### Step 4: Review and Submit

1. Review all entered information
2. Verify uploaded documents are legible
3. Click **"Submit for Review"**
4. Confirm submission in popup dialog

### After Submission

- Award status changes to **"Under Review"**
- You'll receive email confirmation
- Track progress in **"My Awards"** section

---

## Document Management

### Uploading Documents

```
Supported Formats:
├── PDF (recommended for certificates)
├── JPEG (for photos)
└── PNG (for screenshots)
```

**Tips for Best Results:**
- Use high-resolution scans (300 DPI)
- Ensure text is readable
- Keep file sizes under 5MB for faster uploads
- Use descriptive filenames

### Viewing Documents

1. Go to award details
2. Click on document thumbnail
3. Use viewer controls to zoom/rotate
4. Download original if needed

### Replacing Documents

1. Open award in **Draft** status
2. Click **"×"** next to document to remove
3. Upload new document
4. Save changes

---

## Tracking Award Status

### Status Flow

```
Draft → Submitted → Under Review → Approved/Rejected
  ↑                    ↓
  └── Returned for Revision (or Withdrawn by you while nobody has claimed it)
```

### Status Meanings

| Status | Description | Your Action |
|--------|-------------|-------------|
| **Draft** | Not yet submitted | Complete and submit |
| **Submitted** | Awaiting review | Wait for reviewer |
| **Under Review** | Being evaluated | Wait for decision |
| **Returned** | Needs revision | Make requested changes |
| **Approved** | Accepted | None; you decide who else sees it (see Achievements and Visibility) |
| **Rejected** | Not accepted | Review the reason; a rejection is final, enter a corrected award as a new one |

### Checking Status

1. Navigate to **"My Awards"**
2. View status column or filter by status
3. Click award for detailed status history

### Withdrawing and Resubmitting

- **Withdraw**: while nobody has claimed your submitted award, the award page offers **Withdraw**. After the
  confirmation the award is a draft again and the form opens. Once a reviewer has claimed it, the button is gone.
- **Returned award**: the form shows the reviewer's comment above the fields. Change the award or its documents and
  submit it again; it goes back to the level that returned it.
- A withdrawn award, submitted again, starts from the first review level. Earlier decisions stay in its timeline.

### Review History

Each award shows complete history:
- When it was submitted
- Who reviewed it at each level
- Comments from reviewers
- Decision timestamps
- Corrections by a reviewer, with the changed fields and the reason (you also get an e-mail)

---

## Reviewing Awards

For faculty secretaries, deans, the rector's secretary and the rector, and for colleagues acting for them by
delegation.

### The review queue

1. Open **"Reviews"** in the side menu.
2. The tabs show **Mine** (awards you have claimed), **Unassigned** (nobody works on them yet) and **All**.
3. Filter by review level or by faculty or department. Overdue awards come first and carry an **Overdue** chip.
4. Click a row to open the award.

### The faculty review period

Above the tabs, faculty secretaries and deans see how many working days each faculty level has for a request,
for example **"Review period: 3 working days (default)"**.

1. As the dean (or by the dean's delegation), click **"Change"**.
2. Enter 1 to 20 working days and click **"Save"**, or click **"Default period"** to return to the university
   default.
3. The new period applies to deadlines set from now on (a new submission, a resubmission or a move to the dean);
   deadlines already set stay. The rector's levels always keep the default.

### Overdue requests

Once an hour the system checks for open requests whose deadline has passed. Each such request is marked once per
level, and the reviewers of the next level with a reviewer get one e-mail per check listing them («Прострочені
заявки: N / Overdue requests: N»). Nothing moves or decides a request on its own.

1. A marked request carries **Overdue** and **"Manager notified <date>"** in the queue.
2. Tick **"Manager notified"** to list only marked requests; a dean's **"Overdue at the level below"** lists the
   faculty secretaries' marked requests, which the dean may take over.
3. The owner's status page reads **"The review period ended <date>; the manager was notified <date>"**.
4. A move to another level, a return, a resubmission or a withdrawal clears the mark with the new deadline. The
   queue counts in the monitoring metrics are refreshed by the same hourly check.

### Working on an award

The **Review** panel of the award page shows who reviews it and the deadline.

- **Claim**: you become the reviewer; colleagues see your name in the queue.
- **Release**: the award goes back to the queue of its level.
- **Hand over…**: choose a colleague of the same level (colleagues acting by delegation are marked).
- **Take over**: a dean or a higher level takes an award from the reviewer below, after a confirmation naming them;
  a colleague of the same level may do so once the review is overdue. The previous reviewer gets an e-mail.
- **Escalate** is not shown when you already review at the next level (a dean on a secretary-level award decides
  it directly).

If a colleague claimed the award a moment earlier, the panel names them and shows the current state; if the data
was out of date, the page refreshes itself.

### Deciding

You decide on an award you have claimed, or on an unclaimed one (the decision claims it for you). Each button opens
a dialog with a comment field:

- **Approve**: the award is approved when your level is high enough for its category; otherwise it goes to the next
  level and the page says where («Passed to the dean.»). Tick **Documents checked** when the award has documents and
  you checked them: the award gets the verification badge.
- **Return for corrections** (comment required): the award goes back to its owner as a draft with your comment; you
  return to the queue, because the draft is visible only to its owner.
- **Reject** (comment required): the review ends; the owner sees the comment as the reason.
- **Pass to the …**: the award goes to the next level without your approval (not offered to the rector).

The owner gets an e-mail about every decision. The status timeline names the reviewer of each decision, adds
"(on behalf of …)" when the reviewer acted by delegation, and marks a level that was passed over as "skipped".

The **Response template** list in the dialog fills the comment with a prepared text, which you can edit; picking
another template after editing asks before replacing your text.

### Correcting an award

A small mistake (a typo in the title, a wrong date or category) can be fixed without returning the award.

1. On an award you have claimed, or an unclaimed one, click **Correct** in the Review panel (not offered when a
   colleague holds the award).
2. The form shows the current values; the recipient and the documents stay as the owner entered them. Change the
   fields and enter the **Reason for the correction** (up to 1000 characters).
3. **Save** becomes active once a field differs; the confirmation lists every change as old → new before sending.

The correction claims an unclaimed award for you. The award keeps its level and deadline; a new category counts from
the next decision on (a national category needs the rector's secretary). The owner gets an e-mail with the changes,
the reason and your name, and the award history shows **"Corrected by reviewer: <name>"**. The duplicate check is not
run again on a correction.

### Deciding on several awards

1. In the queue, tick the awards (the header box selects the page; Space toggles a row).
2. The action bar offers **Approve (n)**, **Return**, **Reject** and **Pass to the dean** (or **Pass to the next
   level** when the selected awards are at different levels). Up to 50 awards at a time.
3. The dialog takes one comment for all of them, with the same templates.
4. Each award is decided on its own: decided awards leave the list, and the summary **Processed: n of m** lists the
   ones that could not be decided with the reason (for example, claimed by another reviewer) and a link. Only those
   stay selected.

---

## Achievements and Visibility

### Who sees your approved award

An approved award is seen by you and the reviewers whose scope covers it. On its page, the **Visibility** section
offers three choices:

| Choice | Who sees the award |
|--------|--------------------|
| «Лише мені та рецензентам» (default) | You and the reviewers |
| «Показувати колегам» | Every signed-in user, on the achievements page |
| «Показувати публічно» | Anyone, also without an account, on the public achievements page |

Before an award goes public, a dialog lists what is published (your name, department, title, description, category,
awarding organisation, date, link and the verification mark) and what never is (your e-mail, documents, reviewers,
comments). You can switch back at any time; the award leaves the pages at once. Your awards list marks shared
awards with «Колегам» or «Публічно». A unit award is visible to everyone once approved, with no choice.

### The achievements pages

- **Досягнення** in the navigation lists the awards shared with colleagues, newest first, with filters for unit,
  year, level and recipient. The filters stay in the address, so a filtered page can be bookmarked or sent.
- A unit name on a card opens that unit's page (`/units/<id>/achievements`): a department page links to its
  faculty, a faculty page to its departments, and the faculty page includes its departments' awards.
- **Публічна сторінка** opens the same list as the public sees it (`/public/achievements`); on a public page,
  **Переглянути як співробітник** leads back. Public pages need no sign-in and offer **Увійти** and the language
  switch.

---

## My dashboard

**Мій дашборд** in the navigation (`/dashboard`) summarises your approved personal awards. Drafts, pending and
rejected awards and unit awards are not counted.

- **Period**: «З» and «По» choose the first and last year; «Усі роки» clears both. The period stays in the address
  (`/dashboard?from=2022&to=2025`), so a view can be bookmarked.
- **Widgets**, in this order: the summary (approved, with documents verified, highest level, first and latest
  award), the timeline of your awards by date and level, awards by year stacked by level (the levels below the
  university share one colour), by category, by level, and your place in the department.
- **Show as table** («Показати таблицею») above each chart shows the same figures as a table. In the timeline
  table each title opens the award; a point of the timeline chart does the same.
- **Place in the department** is shown only with the consent «Рейтинги й порівняння» in the privacy centre, and
  only in a department with at least five active members. It counts every active member of the department, with
  or without the consent; colleagues are never named. Without the consent the card links to the privacy centre.
- **Customise** («Налаштувати») chooses which widgets to show. The choice is stored for your account, so it follows
  you to another browser, and it is part of your data export.
- **Without approved awards** the dashboard lists the steps to a first award, with **Внести нагороду** and the
  number of awards in review or drafts. When only the chosen period is empty, **Усі роки** goes back to all years.

---

## Managing Your Profile

### Updating Personal Information

1. Click your name in top-right corner
2. Select **"Profile Settings"**
3. Update available fields:
   - Display name
   - Contact information
   - Notification preferences

### Privacy centre

Your name → **Privacy and my data** opens the privacy centre (`/privacy`). Its cards show:

- **Privacy notice**: the version you confirmed and a link to read it again
- **Consents**: whether you may be named in rankings and unit report comparisons, with the history of every change
- **Your choices**: how many approved awards are private, shown to colleagues or public, how many documents colleagues see and how many notification e-mails you switched off, with links to change them
- **Your data**: the download of your data (see below)
- **Your activity**: what you did in the system and what others did with your account, newest first
- **Delete my account**: the erasure request (see below)

### Deleting your account

**Delete my account** lists what is erased (profile, sign-in data, drafts and pending awards, documents, notifications, settings) and what is kept: approved awards under a pseudonym, audit records with your name, address and IP addresses removed, and consent records for seven years after the last change. Enter your password, tick the confirmation box and choose **Request erasure**.

The account is erased 30 days later. Until then you can sign in as usual; a banner on every page names the date, and you receive an e-mail at once and another one a week before the date. **Cancel the erasure** in the privacy centre stops it.

The request is refused when the password is wrong (it counts as a failed sign-in), when the account is locked, when a legal obligation requires the university to keep your data (contact the data protection officer), or when you are the only system administrator (another administrator must be appointed first).

On the date, the erasure runs during the night. You are signed out everywhere and can no longer sign in; a last e-mail confirms the erasure. Your approved awards stay visible only to the reviewers and units that keep them, under the name "Erased #" followed by a number, without their documents. Awards you submitted on behalf of a unit that were still pending are deleted with your account, so the unit's secretary no longer sees them in the queue. Reviews you had claimed go back to the queue of their level, and decisions you made on other people's awards stay under the same pseudonym. The address becomes free and can be used to register a new account.

If a legal obligation arises during the 30 days, the erasure waits until it ends and you receive an e-mail saying so; it then runs at the next night without a new waiting period.

### Notification Preferences

On your profile, the **Notifications** card lists every notification type with two switches, e-mail and in the application. Both are on until you change them. Security and privacy e-mails (sign-in, password, data export, account deletion) are always sent.

### Exporting Your Data

In the privacy centre, **Your data** → **Download my data** saves a JSON file with everything the system holds about you (GDPR Article 20): profile, roles, delegations, awards and their versions, documents, review decisions, consents, devices and your activity. Since format version 1.4 the file also contains:

- `notifications`: your notifications in the application and the log of the notification e-mails sent to you, with when each was sent, read or delivered, and its delivery status
- `notification_preferences`: the notification types you changed from the default
- `legal_holds` (since 1.5): the dates of legal holds on your account, without their reasons
- `erasure_requests` (since 1.6): your erasure requests with their dates and status
- `dashboard_preferences` (since 1.7): the widgets you hid on **My dashboard** and when you saved that choice

You can download the file once a minute; each download is announced to you by e-mail.

---

## Notifications

Decisions, corrections, overdue notices, take-overs, roles and delegations reach you by e-mail and in the application (the bell in the toolbar), as set in your notification preferences. Browser notifications, digests and quiet hours below are not available yet.

### Types of Notifications

| Type | Description | Default |
|------|-------------|---------|
| Status Change | Award approved/rejected/returned | Email + Browser |
| Review Request | Award needs your review | Email + Browser |
| Reminder | Pending items reminder | Weekly email |
| System | Maintenance, updates | Email only |

### Managing Notifications

1. Go to **Profile Settings** → **Notifications**
2. Toggle notifications on/off per type
3. Choose delivery method (email/browser/both)
4. Set quiet hours if needed

---

## FAQ

### General

**Q: Who can see my submitted awards?**  
A: You and the reviewers whose scope covers the award. Once it is approved, you decide whether colleagues or the
public see it too (see [Achievements and Visibility](#achievements-and-visibility)).

**Q: Can I edit an award after submission?**  
A: No, submitted awards cannot be edited. If changes are needed, a reviewer will return it for revision.

**Q: How long does the review process take?**  
A: Typically 3-5 business days for each review level (Department → Faculty → University).

**Q: I received the same email twice. Is something wrong?**  
A: No. When the mail server is unavailable, the system sends the email again later, and in rare cases one
arrives twice. Emails with a confirmation or reset link are not resent; if one does not arrive, request a new link.

### Submission Issues

**Q: My file won't upload. What should I do?**  
A: Check that:
1. File is PDF, JPEG, or PNG
2. File size is under 10MB
3. Filename doesn't contain special characters
4. Browser is up to date

**Q: I submitted the wrong document. How do I fix it?**  
A: Contact your department secretary or system administrator to return the award for revision.

**Q: Can I submit multiple awards at once?**  
A: Each award must be submitted separately for proper tracking and verification.

### Account Issues

**Q: I forgot my password. What do I do?**  
A: Click "Forgot Password" on the login page and follow the email instructions.

**Q: My account is locked. How do I unlock it?**  
A: After 5 failed login attempts, accounts are locked for 30 minutes. Contact IT support if you need immediate access.

**Q: I'm leaving the university. What happens to my awards?**  
A: Approved awards remain in the system. Contact administration about data export if needed.

### Data & Privacy

**Q: How is my data protected?**  
A: All data is encrypted, stored securely, and compliant with GDPR. See Privacy Policy for details.

**Q: Can I delete my data?**  
A: Yes, you can request data deletion under GDPR. Go to Profile → Privacy → "Request Data Deletion".

**Q: Can I export my awards?**  
A: Yes, go to "My Awards" → "Export" to download your awards in PDF or CSV format.

---

## Getting Help

### Contact Support

- **Email**: support@award-system.edu.ua
- **Phone**: +38 (0XX) XXX-XX-XX (Mon-Fri, 9:00-17:00)
- **Help Desk**: Building X, Room XXX

### Useful Links

- [Training Videos](./training/README.md)
- [Admin Guide](./ADMIN_GUIDE.md) (for administrators)
- [Troubleshooting](../operations/TROUBLESHOOTING.md)
- [Privacy Policy](https://award-system.edu.ua/privacy)

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `N` | New award |
| `S` | Search awards |
| `?` | Show shortcuts |
| `Esc` | Close dialog |
| `Enter` | Confirm action |

---

*Last updated: January 2026*

