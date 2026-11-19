# Donation campaigns

## Scope and migration state

Campaign fundraising is implemented alongside the existing Donations module. Existing direct donations continue through their original flow and are not counted toward campaign totals. Campaign progress comes from the campaign ledger and is independent of payouts. Reaching a goal does not close a campaign; deadlines and authorized manual state changes control closure.

The three scoped migrations were reviewed against the existing schema and applied to the configured Supabase test project `hpypmujzzgetvolzkxfw`. A custom-format `pg_dump` backup was created and verified before each application. No database reset or unrelated table change was made. Campaign tables have RLS enabled; browser roles cannot write directly, and campaign writes go through the backend. See `src/main/resources/db/manual/20261118_donation_campaigns.sql`, `20261119_campaign_payment_runtime.sql`, and `20261120_campaign_quote_account_permissions.sql` for the schema, ledger checks, scoped grants, and quote recipient binding. Migration 18 was also exercised against an isolated disposable PostgreSQL cluster with existing donation and unrelated fixture rows preserved; migrations 19 and 20 were applied to the authorized test project after pre-migration backups.

## Campaign lifecycle and moderation

NGO creators and authorized administrators can create and manage campaigns in the NGO profile Donations area. Forms support title, description, cropped/uploaded image, net goal in BRL, deadline, and draft/active/paused/ended states. Server-side checks enforce NGO ownership/administrator permissions, active NGO eligibility, existing verification requirements, and moderation restrictions. Public listing/detail omit inactive, suspended, deadline-ended, and non-active campaigns. Public pages show NGO identity, amount raised, goal/progress, deadline, and a donation action without exposing donor identities.

Campaign reports use the existing content-report pipeline and moderation queue; campaign suspension/restoration records actor, reason, action, and time and uses the existing admin permission. Campaign moderation decisions preserve existing NGO/post report and points behavior. Historical contributions and ledger entries remain after campaign closure.

## Quotes and Stripe boundary

The donor supplies the net NGO amount. The backend calculates and persists an expiring quote with a versioned fee rule and complete BRL breakdown. Checkout requires the quote identifier and explicit confirmation, binds to the quote's connected-account recipient, and ignores client-submitted totals. Campaign checkout supports card only. It uses the existing Stripe Connect direct-charge setup (`RequestOptions.setStripeAccount`) and puts the intended platform share into Stripe's application fee. The success page only polls the backend; it cannot confirm payment.

Campaign payments are disabled unless explicitly enabled for test mode and a test-only fee rule is configured. The checkout adapter rejects live Stripe keys and live-mode sessions. The current enabled fee rule is a clearly marked domestic-card test assumption (5.99% + R$0.39, with a 3.4% gross platform share); it is not production pricing and does not represent verified account-specific pricing. Integer cents and conservative ceiling rounding are used. International-card origin, negotiated connected-account rates, Connect charges, payout costs, refund/dispute fees, and their payer have not been verified for the connected account. Therefore the displayed calculation does not guarantee a net 3.4% platform margin. Unsupported payment methods are disabled for campaigns.

The existing Accounts v2 setup sets `fees_collector=STRIPE` and `losses_collector=STRIPE`; Stripe's public documentation says this assigns direct-charge processing fees to the connected account. This does not establish actual account pricing or all additional costs. No fee rule may become production pricing automatically; production charging remains disabled pending account-specific financial configuration and review.

## Payment ledger

The webhook endpoint verifies Stripe signatures and test-mode events before processing. Event IDs are deduplicated. Contributions are credited only from authoritative Stripe success events after validating the amount, BRL currency, connected account, payment, campaign, and donor metadata. Pending, failed, canceled, refunded, and disputed status changes are ledger-backed; partial refunds use cumulative amounts, disputes debit and reinstate progress through signed deltas, and terminal dispute markers handle out-of-order delivery. Repeated events cannot create a second confirmation or reversal. Actual Stripe processing fees are recorded when Stripe supplies a balance transaction; an estimate shortfall is stored explicitly. Campaign progress is never reduced by withdrawals.

The donor's existing history displays their own campaign donations and payment status. Donor details are not exposed on public campaign pages.

## Validation and known external blockers

The focused Maven suite exercises campaign permissions/lifecycle, fee rounding and rules, quote expiry, checkout idempotency, webhook signature validation, successful/failed/duplicate/out-of-order events, partial/full refunds, disputes, and image references. Frontend browser checks cover public list/detail, quote/login-required behavior, report modal, responsive widths, and empty state. These mocked webhook tests are not a Stripe end-to-end payment.

The configured Stripe credential is test mode, but the read-only Stripe API account request failed with a network error. The environment also has no `STRIPE_CAMPAIGN_WEBHOOK_SECRET`, and campaign payments default disabled. As a result, connected-account configuration/pricing and Stripe test Checkout plus delivered webhook could not be validated here. No live payment or payout was initiated. Public Stripe references used: [direct charges](https://docs.stripe.com/connect/charges#direct-charges), [connected-account balances](https://docs.stripe.com/connect/account-balances), [Accounts v2 responsibilities](https://docs.stripe.com/connect/accounts-v2/connected-account-configuration), [Checkout payment methods](https://docs.stripe.com/payments/checkout/payment-methods), [webhook signatures](https://docs.stripe.com/webhooks), and [Brazil pricing](https://stripe.com/br/pricing).
