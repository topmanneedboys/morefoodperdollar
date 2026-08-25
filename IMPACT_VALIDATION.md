# ValuePilot impact.com Validation

Updated: 2026-08-25

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
impact.com partner account exists. Marketplace/media-partner application was declined on 2026-08-25. No Marketplace approval, advertiser relationship, catalog authorization, production adapter, caching/indexing/display right, or production tracking integration is established.

## Application status

ValuePilot submitted an impact.com partner/Marketplace application on 2026-08-22.

On 2026-08-25 impact.com sent an `Application Update` stating that the application to join Impact as a media partner was declined.

The notification did not provide a concrete, account-specific reason in the message body available to us. Do not infer a cause such as pre-launch status, traffic, website quality, app-store availability, identity, policy mismatch or media-property configuration without direct dashboard/support evidence.

Decision:

**DECLINED / DO NOT CREATE A DUPLICATE ACCOUNT OR BLINDLY RESUBMIT.**

If impact.com is reconsidered, first inspect the authenticated dashboard for a specific rejection reason or remediation path. If none exists, consider one targeted support inquiry rather than repeated applications.

## Partner User Agreement review

A substantial portion of the Partner User Agreement was reviewed from user-provided text through the beginning of Section 6.4. The supplied text was truncated after the opening of Section 6.4, so later provisions must not be treated as reviewed.

### 1. Platform-use scope

impact.com grants a non-exclusive, revocable right to access and use the impact.com technology for partnerships with advertisers and performance under Partner Contracts.

This is a platform-use license. It is not a general license to use, republish or redistribute advertiser data outside the permitted Partner Contract context.

### 2. Application/software use is contemplated, not automatically authorized

The agreement expressly contemplates applications used to deliver Advertiser Content. When an application is used, the partner must disclose its core functionality to visitors, including the functionality that is the media partner's source of revenue, and must meet then-current industry standards for applications, including informed download/install consent where applicable.

This is useful evidence that software/mobile-app participation is not inherently prohibited at the network-agreement level.

However:

- each Advertiser may limit approved promotional methods through Partner Contracts
- network-level permission does not override advertiser-specific restrictions
- application use does not itself grant catalog, caching, display or redistribution rights

### 3. Prohibited methods align with ValuePilot's permanent architecture

The agreement prohibits or restricts methods including:

- scraping or data mining to obtain leads rather than intended visitor action
- fake redirects
- automated mechanisms that generate actions
- robots, iframes or hidden frames used for action generation
- adware, spyware or malware
- cookie stuffing/interception/manipulation of visitor traffic
- deceptive or unauthorized content

These restrictions are compatible with ValuePilot's intended voluntary-click, provider-authorized architecture and reinforce that ValuePilot must not use affiliate-network access as justification for scraping or attribution manipulation.

### 4. Accuracy and quality obligations

Partner content about advertisers and Advertiser Content must be legal, accurate and consistent with Partner Contracts. Publisher websites and promotional methods must remain high quality and comply with law, platform terms, advertiser contracts and applicable standards.

This reinforces ValuePilot's requirement that AI or semantic assistance cannot invent authoritative price, quantity, nutrition, ingredient, product, availability or advertiser-policy evidence.

### 5. Advertiser Data is confidential and contract-scoped

Section 2.2 is especially important for ValuePilot.

impact.com states that Advertiser Data is made available solely in relation to the partner's performance of Partner Contracts and that Advertiser Data constitutes the Advertiser's Confidential Information. Its use is subject to the relevant Partner Contracts.

Therefore:

**impact.com account access or Marketplace approval would not create blanket rights to ingest, cache, index, normalize, display, republish or redistribute advertiser catalogs.**

Any future impact.com integration would require advertiser-/catalog-specific review of:

- permitted data fields
- persistence/caching duration
- indexing/search rights
- display rights
- redistribution restrictions
- API/feed limits
- product identifiers
- price/freshness semantics
- app/mobile permission
- confidentiality constraints

### 6. Advertiser relationships remain advertiser-specific

The agreement makes the partner responsible for choosing advertisers and for those relationships. impact.com may remove or restrict access to Advertiser Content/Data or investigate suspected violations.

This supports ValuePilot's provider model in which:

impact.com account access

!= Marketplace approval

!= advertiser relationship

!= catalog access

!= production-use rights

### 7. Privacy/data-protection obligations

The agreement requires compliance with applicable privacy and data-security laws and incorporates a separate Data Protection Agreement.

Before any production tracking, cookies, personal-data processing or impact.com visitor analytics are enabled, ValuePilot must review the current Data Protection Agreement and update privacy/consent behavior to match actual implementation.

Do not add speculative tracking or consent flows before such functionality exists.

### 8. Confidentiality obligations matter for feed architecture

Reviewed Section 6 requires confidential information to be used only for permitted purposes, protected with reasonable care, and disclosed only to authorized parties. Confidentiality/non-use obligations survive termination. On termination, confidential information may need to be destroyed or returned, subject to limited archival retention.

This means a future impact.com adapter cannot assume indefinite local retention of advertiser data merely because data was once accessible.

Provider-specific retention policy would need to be encoded deliberately rather than relying on a generic permanent cache.

## Strategic conclusion

The Partner User Agreement is structurally compatible with a legitimate shopping-comparison application that uses only approved advertiser methods and provides clear user-requested value.

However, the agreement is deliberately narrow around Advertiser Data and leaves actual promotional/data-use permission to advertiser Partner Contracts. That makes impact.com a potentially useful provider rail, but not a shortcut around advertiser-level authorization or data-rights validation.

The current Marketplace denial closes impact.com as an immediate 5D path. No engineering change is required in response to the denial.

## Next action

1. Do not create a duplicate impact.com account.
2. Do not resubmit blindly.
3. If revisiting impact.com, inspect the authenticated dashboard for an explicit rejection reason/remediation path.
4. If no specific reason exists, one targeted support inquiry may be worthwhile later.
5. If approval is ever obtained, validate advertiser-specific Partner Contracts and actual catalog/feed rights before implementation.
6. Review the remainder of the Partner User Agreement and the incorporated Data Protection Agreement before any production impact.com tracking or personal-data processing.
7. Do not implement an impact.com production adapter during the current 5D validation stage.
