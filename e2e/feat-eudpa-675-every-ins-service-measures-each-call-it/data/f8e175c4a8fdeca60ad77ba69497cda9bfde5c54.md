# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: animals/e2e/features/notification-dashboard-search.spec.ts >> Notification dashboard search >> opens notification view when clicking View after searching by reference number
- Location: tests/animals/e2e/features/notification-dashboard-search.spec.ts:43:3

# Error details

```
FrontendFormError: POST /live-animals/notifications/GBN-AG-26-ZXNMWA/port-of-entry responded 500 instead of a redirect. Body:
<!DOCTYPE html>
<html lang="en" class="govuk-template">
  <head>
    <meta charset="utf-8">
    <title>Arrival details - Import notification service - GOV.UK</title>
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
    <meta name="theme-color" content="#1d70b8">

  <meta name="csrf-token" content="fipbgym_Ec32EPPTMBb9ohJxNXcG8-IndNjav5M92aM">
  <link href="/public/stylesheets/application.e837a95.min.css" rel="stylesheet">

      <link rel="icon" sizes="48x48" href="/public/assets/images/favicon.ico">
      <link rel="icon" sizes="any" href="/public/assets/images/favicon.svg" type="image/svg+xml">
      <link rel="mask-icon" href="/public/assets/images/govuk-icon-mask.svg" color="#1d70b8">
      <link rel="apple-touch-icon" href="/public/assets/images/govuk-icon-180.png">
      <link rel="manifest" href="/public/assets/manifest.json">

  </head>
  <body class="govuk-template__body">
    <script>document.body.className += ' js-enabled' + ('noModule' in HTMLScriptElement.prototype ? ' govuk-frontend-supported' : '');</script>

        <a href="#main-content" class="govuk-skip-link" data-module="govuk-skip-link">Skip to main content</a>


    

        <header class="govuk-template__header">
            


<div class="govuk-header app-header">
  <div class="govuk-header__container govuk-width-container">
    <div class="govuk-header__logo">
      <a href="https://www.gov.uk/" class="govuk-header__homepage-link">
          <svg
            focusable="false"
            role="img"
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 324 60"
            height="30"
            width="162"
            fill="currentcolor" class="govuk-header__logotype" aria-label="GOV.UK"
          ><title>GOV.UK</title>    <g>
                <circle cx="20" cy="17.6" r="3.7"/>
                <circle cx="10.2" cy="23.5" r="3.7"/>
                <circle cx="3.7" cy="33.2" r="3.7"/>
                <circle cx="31.7" cy="30.6" r=
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - link "Skip to main content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - banner [ref=e3]:
    - link [ref=e7] [cursor=pointer]:
      - /url: https://www.gov.uk/
      - img "GOV.UK" [ref=e8]
    - region "Service information" [ref=e21]:
      - generic [ref=e23]:
        - link "Import notification service" [ref=e25] [cursor=pointer]:
          - /url: /live-animals
        - navigation "Menu" [ref=e26]:
          - list [ref=e27]:
            - listitem [ref=e28]:
              - link [ref=e29] [cursor=pointer]:
                - /url: /live-animals
                - strong [ref=e30]: Dashboard
            - listitem [ref=e31]:
              - link "Address book" [ref=e32] [cursor=pointer]:
                - /url: http://localhost:3002/address-book
            - listitem [ref=e33]:
              - link "Manage account" [ref=e34] [cursor=pointer]:
                - /url: "#"
            - listitem [ref=e35]:
              - link "Log out" [ref=e36] [cursor=pointer]:
                - /url: /auth/sign-out
  - generic [ref=e37]:
    - paragraph [ref=e39]:
      - strong [ref=e40]: Alpha
      - generic [ref=e41]:
        - text: This is a new service. Help us improve it and
        - link "give your feedback by email" [ref=e42] [cursor=pointer]:
          - /url: mailto:APHAServiceDesk@apha.gov.uk
        - text: .
    - main [ref=e43]:
      - generic [ref=e45]:
        - generic [ref=e46]: Dashboard
        - heading "Import notification service" [level=1] [ref=e47]
        - paragraph [ref=e48]: Use this service to tell the authorities about live animals you are importing. You will answer a short set of questions about the consignment, then submit your notification.
        - button "Start a new notification" [ref=e50] [cursor=pointer]
        - heading "Your notifications" [level=2] [ref=e53]
        - generic [ref=e54]:
          - complementary [ref=e56]:
            - heading "Filter notifications" [level=3] [ref=e57]
            - generic [ref=e58]:
              - generic [ref=e59]:
                - generic [ref=e60]: Keyword or reference
                - textbox "Keyword or reference" [ref=e61]
              - button "Search" [ref=e62] [cursor=pointer]
          - generic [ref=e63]:
            - generic [ref=e64]:
              - paragraph [ref=e65]: Showing 1 to 25 of 65 Results
              - generic [ref=e66]:
                - generic [ref=e67]:
                  - generic [ref=e68]: Sort by
                  - combobox "Sort by" [ref=e69]:
                    - option "Arrival (newest to oldest)" [selected]
                    - option "Arrival (oldest to newest)"
                    - option "Date created (newest to oldest)"
                    - option "Date created (oldest to newest)"
                - button "Update sort" [ref=e70] [cursor=pointer]
            - generic [ref=e71]:
              - generic [ref=e72]:
                - heading "GBN-AG-26-VFWNR7" [level=3] [ref=e73]
                - list [ref=e74]:
                  - listitem [ref=e75]:
                    - link "Resume notification GBN-AG-26-VFWNR7" [ref=e76] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-VFWNR7
                      - text: Resume
                      - generic [ref=e77]: notification GBN-AG-26-VFWNR7
                  - listitem [ref=e78]:
                    - button "Copy as new notification GBN-AG-26-VFWNR7" [ref=e80] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e81]: notification GBN-AG-26-VFWNR7
                  - listitem [ref=e82]:
                    - link "Delete notification GBN-AG-26-VFWNR7" [ref=e83] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-VFWNR7/delete
                      - text: Delete
                      - generic [ref=e84]: notification GBN-AG-26-VFWNR7
              - generic [ref=e85]:
                - generic [ref=e86]:
                  - generic [ref=e87]:
                    - term [ref=e88]: Commodity
                    - definition [ref=e89]: Cow
                  - generic [ref=e90]:
                    - term [ref=e91]: Origin
                    - definition [ref=e92]: France
                  - generic [ref=e93]:
                    - term [ref=e94]: Arrival at destination
                    - definition
                - generic [ref=e95]:
                  - generic [ref=e96]:
                    - term [ref=e97]: Consignee
                    - definition
                  - generic [ref=e98]:
                    - term [ref=e99]: Consignor
                    - definition
                  - generic [ref=e100]:
                    - term [ref=e101]: Status
                    - definition [ref=e102]:
                      - strong [ref=e103]: Draft
                - generic [ref=e104]:
                  - generic [ref=e105]:
                    - term [ref=e106]: Date created
                    - definition [ref=e107]: 7 Oct 2026
                  - generic [ref=e108]:
                    - term [ref=e109]: Date submitted
                    - definition
            - generic [ref=e110]:
              - generic [ref=e111]:
                - heading "GBN-AG-26-640JNN" [level=3] [ref=e112]
                - list [ref=e113]:
                  - listitem [ref=e114]:
                    - link "Resume notification GBN-AG-26-640JNN" [ref=e115] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-640JNN
                      - text: Resume
                      - generic [ref=e116]: notification GBN-AG-26-640JNN
                  - listitem [ref=e117]:
                    - button "Copy as new notification GBN-AG-26-640JNN" [ref=e119] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e120]: notification GBN-AG-26-640JNN
                  - listitem [ref=e121]:
                    - link "Delete notification GBN-AG-26-640JNN" [ref=e122] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-640JNN/delete
                      - text: Delete
                      - generic [ref=e123]: notification GBN-AG-26-640JNN
              - generic [ref=e124]:
                - generic [ref=e125]:
                  - generic [ref=e126]:
                    - term [ref=e127]: Commodity
                    - definition [ref=e128]: Cow
                  - generic [ref=e129]:
                    - term [ref=e130]: Origin
                    - definition [ref=e131]: France
                  - generic [ref=e132]:
                    - term [ref=e133]: Arrival at destination
                    - definition
                - generic [ref=e134]:
                  - generic [ref=e135]:
                    - term [ref=e136]: Consignee
                    - definition [ref=e137]: British Livestock Ltd
                  - generic [ref=e138]:
                    - term [ref=e139]: Consignor
                    - definition [ref=e140]: Astra Rosales
                  - generic [ref=e141]:
                    - term [ref=e142]: Status
                    - definition [ref=e143]:
                      - strong [ref=e144]: Draft
                - generic [ref=e145]:
                  - generic [ref=e146]:
                    - term [ref=e147]: Date created
                    - definition [ref=e148]: 7 Oct 2026
                  - generic [ref=e149]:
                    - term [ref=e150]: Date submitted
                    - definition
            - generic [ref=e151]:
              - generic [ref=e152]:
                - heading "GBN-AG-26-WK2D5M" [level=3] [ref=e153]
                - list [ref=e154]:
                  - listitem [ref=e155]:
                    - link "Resume notification GBN-AG-26-WK2D5M" [ref=e156] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-WK2D5M
                      - text: Resume
                      - generic [ref=e157]: notification GBN-AG-26-WK2D5M
                  - listitem [ref=e158]:
                    - button "Copy as new notification GBN-AG-26-WK2D5M" [ref=e160] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e161]: notification GBN-AG-26-WK2D5M
                  - listitem [ref=e162]:
                    - link "Delete notification GBN-AG-26-WK2D5M" [ref=e163] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-WK2D5M/delete
                      - text: Delete
                      - generic [ref=e164]: notification GBN-AG-26-WK2D5M
              - generic [ref=e165]:
                - generic [ref=e166]:
                  - generic [ref=e167]:
                    - term [ref=e168]: Commodity
                    - definition [ref=e169]: Cow
                  - generic [ref=e170]:
                    - term [ref=e171]: Origin
                    - definition [ref=e172]: France
                  - generic [ref=e173]:
                    - term [ref=e174]: Arrival at destination
                    - definition
                - generic [ref=e175]:
                  - generic [ref=e176]:
                    - term [ref=e177]: Consignee
                    - definition
                  - generic [ref=e178]:
                    - term [ref=e179]: Consignor
                    - definition
                  - generic [ref=e180]:
                    - term [ref=e181]: Status
                    - definition [ref=e182]:
                      - strong [ref=e183]: Draft
                - generic [ref=e184]:
                  - generic [ref=e185]:
                    - term [ref=e186]: Date created
                    - definition [ref=e187]: 7 Oct 2026
                  - generic [ref=e188]:
                    - term [ref=e189]: Date submitted
                    - definition
            - generic [ref=e190]:
              - generic [ref=e191]:
                - heading "GBN-AG-26-2BWMJS" [level=3] [ref=e192]
                - list [ref=e193]:
                  - listitem [ref=e194]:
                    - link "Resume notification GBN-AG-26-2BWMJS" [ref=e195] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-2BWMJS
                      - text: Resume
                      - generic [ref=e196]: notification GBN-AG-26-2BWMJS
                  - listitem [ref=e197]:
                    - button "Copy as new notification GBN-AG-26-2BWMJS" [ref=e199] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e200]: notification GBN-AG-26-2BWMJS
                  - listitem [ref=e201]:
                    - link "Delete notification GBN-AG-26-2BWMJS" [ref=e202] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-2BWMJS/delete
                      - text: Delete
                      - generic [ref=e203]: notification GBN-AG-26-2BWMJS
              - generic [ref=e204]:
                - generic [ref=e205]:
                  - generic [ref=e206]:
                    - term [ref=e207]: Commodity
                    - definition [ref=e208]: Cow
                  - generic [ref=e209]:
                    - term [ref=e210]: Origin
                    - definition [ref=e211]: France
                  - generic [ref=e212]:
                    - term [ref=e213]: Arrival at destination
                    - definition
                - generic [ref=e214]:
                  - generic [ref=e215]:
                    - term [ref=e216]: Consignee
                    - definition
                  - generic [ref=e217]:
                    - term [ref=e218]: Consignor
                    - definition
                  - generic [ref=e219]:
                    - term [ref=e220]: Status
                    - definition [ref=e221]:
                      - strong [ref=e222]: Draft
                - generic [ref=e223]:
                  - generic [ref=e224]:
                    - term [ref=e225]: Date created
                    - definition [ref=e226]: 7 Oct 2026
                  - generic [ref=e227]:
                    - term [ref=e228]: Date submitted
                    - definition
            - generic [ref=e229]:
              - generic [ref=e230]:
                - heading "GBN-AG-26-4Q87RX" [level=3] [ref=e231]
                - list [ref=e232]:
                  - listitem [ref=e233]:
                    - link "Resume notification GBN-AG-26-4Q87RX" [ref=e234] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-4Q87RX
                      - text: Resume
                      - generic [ref=e235]: notification GBN-AG-26-4Q87RX
                  - listitem [ref=e236]:
                    - button "Copy as new notification GBN-AG-26-4Q87RX" [ref=e238] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e239]: notification GBN-AG-26-4Q87RX
                  - listitem [ref=e240]:
                    - link "Delete notification GBN-AG-26-4Q87RX" [ref=e241] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-4Q87RX/delete
                      - text: Delete
                      - generic [ref=e242]: notification GBN-AG-26-4Q87RX
              - generic [ref=e243]:
                - generic [ref=e244]:
                  - generic [ref=e245]:
                    - term [ref=e246]: Commodity
                    - definition [ref=e247]: Cow
                  - generic [ref=e248]:
                    - term [ref=e249]: Origin
                    - definition [ref=e250]: France
                  - generic [ref=e251]:
                    - term [ref=e252]: Arrival at destination
                    - definition
                - generic [ref=e253]:
                  - generic [ref=e254]:
                    - term [ref=e255]: Consignee
                    - definition
                  - generic [ref=e256]:
                    - term [ref=e257]: Consignor
                    - definition
                  - generic [ref=e258]:
                    - term [ref=e259]: Status
                    - definition [ref=e260]:
                      - strong [ref=e261]: Draft
                - generic [ref=e262]:
                  - generic [ref=e263]:
                    - term [ref=e264]: Date created
                    - definition [ref=e265]: 7 Oct 2026
                  - generic [ref=e266]:
                    - term [ref=e267]: Date submitted
                    - definition
            - generic [ref=e268]:
              - generic [ref=e269]:
                - heading "GBN-AG-26-XJ5H2D" [level=3] [ref=e270]
                - list [ref=e271]:
                  - listitem [ref=e272]:
                    - link "Resume notification GBN-AG-26-XJ5H2D" [ref=e273] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XJ5H2D
                      - text: Resume
                      - generic [ref=e274]: notification GBN-AG-26-XJ5H2D
                  - listitem [ref=e275]:
                    - button "Copy as new notification GBN-AG-26-XJ5H2D" [ref=e277] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e278]: notification GBN-AG-26-XJ5H2D
                  - listitem [ref=e279]:
                    - link "Delete notification GBN-AG-26-XJ5H2D" [ref=e280] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XJ5H2D/delete
                      - text: Delete
                      - generic [ref=e281]: notification GBN-AG-26-XJ5H2D
              - generic [ref=e282]:
                - generic [ref=e283]:
                  - generic [ref=e284]:
                    - term [ref=e285]: Commodity
                    - definition [ref=e286]: Cow
                  - generic [ref=e287]:
                    - term [ref=e288]: Origin
                    - definition [ref=e289]: France
                  - generic [ref=e290]:
                    - term [ref=e291]: Arrival at destination
                    - definition
                - generic [ref=e292]:
                  - generic [ref=e293]:
                    - term [ref=e294]: Consignee
                    - definition [ref=e295]: British Livestock Ltd
                  - generic [ref=e296]:
                    - term [ref=e297]: Consignor
                    - definition [ref=e298]: Astra Rosales
                  - generic [ref=e299]:
                    - term [ref=e300]: Status
                    - definition [ref=e301]:
                      - strong [ref=e302]: Draft
                - generic [ref=e303]:
                  - generic [ref=e304]:
                    - term [ref=e305]: Date created
                    - definition [ref=e306]: 7 Oct 2026
                  - generic [ref=e307]:
                    - term [ref=e308]: Date submitted
                    - definition
            - generic [ref=e309]:
              - generic [ref=e310]:
                - heading "GBN-AG-26-XSRJ2F" [level=3] [ref=e311]
                - list [ref=e312]:
                  - listitem [ref=e313]:
                    - link "Resume notification GBN-AG-26-XSRJ2F" [ref=e314] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XSRJ2F
                      - text: Resume
                      - generic [ref=e315]: notification GBN-AG-26-XSRJ2F
                  - listitem [ref=e316]:
                    - button "Copy as new notification GBN-AG-26-XSRJ2F" [ref=e318] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e319]: notification GBN-AG-26-XSRJ2F
                  - listitem [ref=e320]:
                    - link "Delete notification GBN-AG-26-XSRJ2F" [ref=e321] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XSRJ2F/delete
                      - text: Delete
                      - generic [ref=e322]: notification GBN-AG-26-XSRJ2F
              - generic [ref=e323]:
                - generic [ref=e324]:
                  - generic [ref=e325]:
                    - term [ref=e326]: Commodity
                    - definition [ref=e327]: Cow
                  - generic [ref=e328]:
                    - term [ref=e329]: Origin
                    - definition [ref=e330]: France
                  - generic [ref=e331]:
                    - term [ref=e332]: Arrival at destination
                    - definition
                - generic [ref=e333]:
                  - generic [ref=e334]:
                    - term [ref=e335]: Consignee
                    - definition [ref=e336]: British Livestock Ltd
                  - generic [ref=e337]:
                    - term [ref=e338]: Consignor
                    - definition [ref=e339]: Astra Rosales
                  - generic [ref=e340]:
                    - term [ref=e341]: Status
                    - definition [ref=e342]:
                      - strong [ref=e343]: Draft
                - generic [ref=e344]:
                  - generic [ref=e345]:
                    - term [ref=e346]: Date created
                    - definition [ref=e347]: 7 Oct 2026
                  - generic [ref=e348]:
                    - term [ref=e349]: Date submitted
                    - definition
            - generic [ref=e350]:
              - generic [ref=e351]:
                - heading "GBN-AG-26-KRZYF9" [level=3] [ref=e352]
                - list [ref=e353]:
                  - listitem [ref=e354]:
                    - link "Resume notification GBN-AG-26-KRZYF9" [ref=e355] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-KRZYF9
                      - text: Resume
                      - generic [ref=e356]: notification GBN-AG-26-KRZYF9
                  - listitem [ref=e357]:
                    - button "Copy as new notification GBN-AG-26-KRZYF9" [ref=e359] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e360]: notification GBN-AG-26-KRZYF9
                  - listitem [ref=e361]:
                    - link "Delete notification GBN-AG-26-KRZYF9" [ref=e362] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-KRZYF9/delete
                      - text: Delete
                      - generic [ref=e363]: notification GBN-AG-26-KRZYF9
              - generic [ref=e364]:
                - generic [ref=e365]:
                  - generic [ref=e366]:
                    - term [ref=e367]: Commodity
                    - definition [ref=e368]: Cow
                  - generic [ref=e369]:
                    - term [ref=e370]: Origin
                    - definition [ref=e371]: France
                  - generic [ref=e372]:
                    - term [ref=e373]: Arrival at destination
                    - definition
                - generic [ref=e374]:
                  - generic [ref=e375]:
                    - term [ref=e376]: Consignee
                    - definition
                  - generic [ref=e377]:
                    - term [ref=e378]: Consignor
                    - definition
                  - generic [ref=e379]:
                    - term [ref=e380]: Status
                    - definition [ref=e381]:
                      - strong [ref=e382]: Draft
                - generic [ref=e383]:
                  - generic [ref=e384]:
                    - term [ref=e385]: Date created
                    - definition [ref=e386]: 7 Oct 2026
                  - generic [ref=e387]:
                    - term [ref=e388]: Date submitted
                    - definition
            - generic [ref=e389]:
              - generic [ref=e390]:
                - heading "GBN-AG-26-82YCAE" [level=3] [ref=e391]
                - list [ref=e392]:
                  - listitem [ref=e393]:
                    - link "Resume notification GBN-AG-26-82YCAE" [ref=e394] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-82YCAE
                      - text: Resume
                      - generic [ref=e395]: notification GBN-AG-26-82YCAE
                  - listitem [ref=e396]:
                    - button "Copy as new notification GBN-AG-26-82YCAE" [ref=e398] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e399]: notification GBN-AG-26-82YCAE
                  - listitem [ref=e400]:
                    - link "Delete notification GBN-AG-26-82YCAE" [ref=e401] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-82YCAE/delete
                      - text: Delete
                      - generic [ref=e402]: notification GBN-AG-26-82YCAE
              - generic [ref=e403]:
                - generic [ref=e404]:
                  - generic [ref=e405]:
                    - term [ref=e406]: Commodity
                    - definition [ref=e407]: Cow
                  - generic [ref=e408]:
                    - term [ref=e409]: Origin
                    - definition [ref=e410]: France
                  - generic [ref=e411]:
                    - term [ref=e412]: Arrival at destination
                    - definition
                - generic [ref=e413]:
                  - generic [ref=e414]:
                    - term [ref=e415]: Consignee
                    - definition [ref=e416]: British Livestock Ltd
                  - generic [ref=e417]:
                    - term [ref=e418]: Consignor
                    - definition [ref=e419]: Astra Rosales
                  - generic [ref=e420]:
                    - term [ref=e421]: Status
                    - definition [ref=e422]:
                      - strong [ref=e423]: Draft
                - generic [ref=e424]:
                  - generic [ref=e425]:
                    - term [ref=e426]: Date created
                    - definition [ref=e427]: 7 Oct 2026
                  - generic [ref=e428]:
                    - term [ref=e429]: Date submitted
                    - definition
            - generic [ref=e430]:
              - generic [ref=e431]:
                - heading "GBN-AG-26-XACYNP" [level=3] [ref=e432]
                - list [ref=e433]:
                  - listitem [ref=e434]:
                    - link "Resume notification GBN-AG-26-XACYNP" [ref=e435] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XACYNP
                      - text: Resume
                      - generic [ref=e436]: notification GBN-AG-26-XACYNP
                  - listitem [ref=e437]:
                    - button "Copy as new notification GBN-AG-26-XACYNP" [ref=e439] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e440]: notification GBN-AG-26-XACYNP
                  - listitem [ref=e441]:
                    - link "Delete notification GBN-AG-26-XACYNP" [ref=e442] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XACYNP/delete
                      - text: Delete
                      - generic [ref=e443]: notification GBN-AG-26-XACYNP
              - generic [ref=e444]:
                - generic [ref=e445]:
                  - generic [ref=e446]:
                    - term [ref=e447]: Commodity
                    - definition [ref=e448]: Cow
                  - generic [ref=e449]:
                    - term [ref=e450]: Origin
                    - definition [ref=e451]: France
                  - generic [ref=e452]:
                    - term [ref=e453]: Arrival at destination
                    - definition
                - generic [ref=e454]:
                  - generic [ref=e455]:
                    - term [ref=e456]: Consignee
                    - definition
                  - generic [ref=e457]:
                    - term [ref=e458]: Consignor
                    - definition
                  - generic [ref=e459]:
                    - term [ref=e460]: Status
                    - definition [ref=e461]:
                      - strong [ref=e462]: Draft
                - generic [ref=e463]:
                  - generic [ref=e464]:
                    - term [ref=e465]: Date created
                    - definition [ref=e466]: 7 Oct 2026
                  - generic [ref=e467]:
                    - term [ref=e468]: Date submitted
                    - definition
            - generic [ref=e469]:
              - generic [ref=e470]:
                - heading "GBN-AG-26-GJSQK9" [level=3] [ref=e471]
                - list [ref=e472]:
                  - listitem [ref=e473]:
                    - link "Resume notification GBN-AG-26-GJSQK9" [ref=e474] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-GJSQK9
                      - text: Resume
                      - generic [ref=e475]: notification GBN-AG-26-GJSQK9
                  - listitem [ref=e476]:
                    - button "Copy as new notification GBN-AG-26-GJSQK9" [ref=e478] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e479]: notification GBN-AG-26-GJSQK9
                  - listitem [ref=e480]:
                    - link "Delete notification GBN-AG-26-GJSQK9" [ref=e481] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-GJSQK9/delete
                      - text: Delete
                      - generic [ref=e482]: notification GBN-AG-26-GJSQK9
              - generic [ref=e483]:
                - generic [ref=e484]:
                  - generic [ref=e485]:
                    - term [ref=e486]: Commodity
                    - definition [ref=e487]: Cow
                  - generic [ref=e488]:
                    - term [ref=e489]: Origin
                    - definition [ref=e490]: France
                  - generic [ref=e491]:
                    - term [ref=e492]: Arrival at destination
                    - definition
                - generic [ref=e493]:
                  - generic [ref=e494]:
                    - term [ref=e495]: Consignee
                    - definition
                  - generic [ref=e496]:
                    - term [ref=e497]: Consignor
                    - definition
                  - generic [ref=e498]:
                    - term [ref=e499]: Status
                    - definition [ref=e500]:
                      - strong [ref=e501]: Draft
                - generic [ref=e502]:
                  - generic [ref=e503]:
                    - term [ref=e504]: Date created
                    - definition [ref=e505]: 7 Oct 2026
                  - generic [ref=e506]:
                    - term [ref=e507]: Date submitted
                    - definition
            - generic [ref=e508]:
              - generic [ref=e509]:
                - heading "GBN-AG-26-36F253" [level=3] [ref=e510]
                - list [ref=e511]:
                  - listitem [ref=e512]:
                    - link "Resume notification GBN-AG-26-36F253" [ref=e513] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-36F253
                      - text: Resume
                      - generic [ref=e514]: notification GBN-AG-26-36F253
                  - listitem [ref=e515]:
                    - button "Copy as new notification GBN-AG-26-36F253" [ref=e517] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e518]: notification GBN-AG-26-36F253
                  - listitem [ref=e519]:
                    - link "Delete notification GBN-AG-26-36F253" [ref=e520] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-36F253/delete
                      - text: Delete
                      - generic [ref=e521]: notification GBN-AG-26-36F253
              - generic [ref=e522]:
                - generic [ref=e523]:
                  - generic [ref=e524]:
                    - term [ref=e525]: Commodity
                    - definition [ref=e526]: Cow
                  - generic [ref=e527]:
                    - term [ref=e528]: Origin
                    - definition [ref=e529]: France
                  - generic [ref=e530]:
                    - term [ref=e531]: Arrival at destination
                    - definition
                - generic [ref=e532]:
                  - generic [ref=e533]:
                    - term [ref=e534]: Consignee
                    - definition
                  - generic [ref=e535]:
                    - term [ref=e536]: Consignor
                    - definition
                  - generic [ref=e537]:
                    - term [ref=e538]: Status
                    - definition [ref=e539]:
                      - strong [ref=e540]: Draft
                - generic [ref=e541]:
                  - generic [ref=e542]:
                    - term [ref=e543]: Date created
                    - definition [ref=e544]: 7 Oct 2026
                  - generic [ref=e545]:
                    - term [ref=e546]: Date submitted
                    - definition
            - generic [ref=e547]:
              - generic [ref=e548]:
                - heading "GBN-AG-26-XQQ1TS" [level=3] [ref=e549]
                - list [ref=e550]:
                  - listitem [ref=e551]:
                    - link "Resume notification GBN-AG-26-XQQ1TS" [ref=e552] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XQQ1TS
                      - text: Resume
                      - generic [ref=e553]: notification GBN-AG-26-XQQ1TS
                  - listitem [ref=e554]:
                    - button "Copy as new notification GBN-AG-26-XQQ1TS" [ref=e556] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e557]: notification GBN-AG-26-XQQ1TS
                  - listitem [ref=e558]:
                    - link "Delete notification GBN-AG-26-XQQ1TS" [ref=e559] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-XQQ1TS/delete
                      - text: Delete
                      - generic [ref=e560]: notification GBN-AG-26-XQQ1TS
              - generic [ref=e561]:
                - generic [ref=e562]:
                  - generic [ref=e563]:
                    - term [ref=e564]: Commodity
                    - definition [ref=e565]: Cow
                  - generic [ref=e566]:
                    - term [ref=e567]: Origin
                    - definition [ref=e568]: France
                  - generic [ref=e569]:
                    - term [ref=e570]: Arrival at destination
                    - definition
                - generic [ref=e571]:
                  - generic [ref=e572]:
                    - term [ref=e573]: Consignee
                    - definition
                  - generic [ref=e574]:
                    - term [ref=e575]: Consignor
                    - definition [ref=e576]: Paged Consignor 1791369264781
                  - generic [ref=e577]:
                    - term [ref=e578]: Status
                    - definition [ref=e579]:
                      - strong [ref=e580]: Draft
                - generic [ref=e581]:
                  - generic [ref=e582]:
                    - term [ref=e583]: Date created
                    - definition [ref=e584]: 7 Oct 2026
                  - generic [ref=e585]:
                    - term [ref=e586]: Date submitted
                    - definition
            - generic [ref=e587]:
              - generic [ref=e588]:
                - heading "GBN-AG-26-WPKB5N" [level=3] [ref=e589]
                - list [ref=e590]:
                  - listitem [ref=e591]:
                    - link "Resume notification GBN-AG-26-WPKB5N" [ref=e592] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-WPKB5N
                      - text: Resume
                      - generic [ref=e593]: notification GBN-AG-26-WPKB5N
                  - listitem [ref=e594]:
                    - button "Copy as new notification GBN-AG-26-WPKB5N" [ref=e596] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e597]: notification GBN-AG-26-WPKB5N
                  - listitem [ref=e598]:
                    - link "Delete notification GBN-AG-26-WPKB5N" [ref=e599] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-WPKB5N/delete
                      - text: Delete
                      - generic [ref=e600]: notification GBN-AG-26-WPKB5N
              - generic [ref=e601]:
                - generic [ref=e602]:
                  - generic [ref=e603]:
                    - term [ref=e604]: Commodity
                    - definition [ref=e605]: Cow
                  - generic [ref=e606]:
                    - term [ref=e607]: Origin
                    - definition [ref=e608]: France
                  - generic [ref=e609]:
                    - term [ref=e610]: Arrival at destination
                    - definition
                - generic [ref=e611]:
                  - generic [ref=e612]:
                    - term [ref=e613]: Consignee
                    - definition
                  - generic [ref=e614]:
                    - term [ref=e615]: Consignor
                    - definition
                  - generic [ref=e616]:
                    - term [ref=e617]: Status
                    - definition [ref=e618]:
                      - strong [ref=e619]: Draft
                - generic [ref=e620]:
                  - generic [ref=e621]:
                    - term [ref=e622]: Date created
                    - definition [ref=e623]: 7 Oct 2026
                  - generic [ref=e624]:
                    - term [ref=e625]: Date submitted
                    - definition
            - generic [ref=e626]:
              - generic [ref=e627]:
                - heading "GBN-AG-26-CDYRHA" [level=3] [ref=e628]
                - list [ref=e629]:
                  - listitem [ref=e630]:
                    - link "Resume notification GBN-AG-26-CDYRHA" [ref=e631] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-CDYRHA
                      - text: Resume
                      - generic [ref=e632]: notification GBN-AG-26-CDYRHA
                  - listitem [ref=e633]:
                    - button "Copy as new notification GBN-AG-26-CDYRHA" [ref=e635] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e636]: notification GBN-AG-26-CDYRHA
                  - listitem [ref=e637]:
                    - link "Delete notification GBN-AG-26-CDYRHA" [ref=e638] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-CDYRHA/delete
                      - text: Delete
                      - generic [ref=e639]: notification GBN-AG-26-CDYRHA
              - generic [ref=e640]:
                - generic [ref=e641]:
                  - generic [ref=e642]:
                    - term [ref=e643]: Commodity
                    - definition [ref=e644]: Cow
                  - generic [ref=e645]:
                    - term [ref=e646]: Origin
                    - definition [ref=e647]: France
                  - generic [ref=e648]:
                    - term [ref=e649]: Arrival at destination
                    - definition
                - generic [ref=e650]:
                  - generic [ref=e651]:
                    - term [ref=e652]: Consignee
                    - definition
                  - generic [ref=e653]:
                    - term [ref=e654]: Consignor
                    - definition [ref=e655]: Renamed Holding 1791369258938
                  - generic [ref=e656]:
                    - term [ref=e657]: Status
                    - definition [ref=e658]:
                      - strong [ref=e659]: Draft
                - generic [ref=e660]:
                  - generic [ref=e661]:
                    - term [ref=e662]: Date created
                    - definition [ref=e663]: 7 Oct 2026
                  - generic [ref=e664]:
                    - term [ref=e665]: Date submitted
                    - definition
            - generic [ref=e666]:
              - generic [ref=e667]:
                - heading "GBN-AG-26-2EV0Y6" [level=3] [ref=e668]
                - list [ref=e669]:
                  - listitem [ref=e670]:
                    - link "Resume notification GBN-AG-26-2EV0Y6" [ref=e671] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-2EV0Y6
                      - text: Resume
                      - generic [ref=e672]: notification GBN-AG-26-2EV0Y6
                  - listitem [ref=e673]:
                    - button "Copy as new notification GBN-AG-26-2EV0Y6" [ref=e675] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e676]: notification GBN-AG-26-2EV0Y6
                  - listitem [ref=e677]:
                    - link "Delete notification GBN-AG-26-2EV0Y6" [ref=e678] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-2EV0Y6/delete
                      - text: Delete
                      - generic [ref=e679]: notification GBN-AG-26-2EV0Y6
              - generic [ref=e680]:
                - generic [ref=e681]:
                  - generic [ref=e682]:
                    - term [ref=e683]: Commodity
                    - definition
                  - generic [ref=e684]:
                    - term [ref=e685]: Origin
                    - definition [ref=e686]: France
                  - generic [ref=e687]:
                    - term [ref=e688]: Arrival at destination
                    - definition
                - generic [ref=e689]:
                  - generic [ref=e690]:
                    - term [ref=e691]: Consignee
                    - definition
                  - generic [ref=e692]:
                    - term [ref=e693]: Consignor
                    - definition
                  - generic [ref=e694]:
                    - term [ref=e695]: Status
                    - definition [ref=e696]:
                      - strong [ref=e697]: Draft
                - generic [ref=e698]:
                  - generic [ref=e699]:
                    - term [ref=e700]: Date created
                    - definition [ref=e701]: 7 Oct 2026
                  - generic [ref=e702]:
                    - term [ref=e703]: Date submitted
                    - definition
            - generic [ref=e704]:
              - generic [ref=e705]:
                - heading "GBN-AG-26-MZD9VT" [level=3] [ref=e706]
                - list [ref=e707]:
                  - listitem [ref=e708]:
                    - link "Resume notification GBN-AG-26-MZD9VT" [ref=e709] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-MZD9VT
                      - text: Resume
                      - generic [ref=e710]: notification GBN-AG-26-MZD9VT
                  - listitem [ref=e711]:
                    - button "Copy as new notification GBN-AG-26-MZD9VT" [ref=e713] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e714]: notification GBN-AG-26-MZD9VT
                  - listitem [ref=e715]:
                    - link "Delete notification GBN-AG-26-MZD9VT" [ref=e716] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-MZD9VT/delete
                      - text: Delete
                      - generic [ref=e717]: notification GBN-AG-26-MZD9VT
              - generic [ref=e718]:
                - generic [ref=e719]:
                  - generic [ref=e720]:
                    - term [ref=e721]: Commodity
                    - definition [ref=e722]: Cow
                  - generic [ref=e723]:
                    - term [ref=e724]: Origin
                    - definition [ref=e725]: France
                  - generic [ref=e726]:
                    - term [ref=e727]: Arrival at destination
                    - definition
                - generic [ref=e728]:
                  - generic [ref=e729]:
                    - term [ref=e730]: Consignee
                    - definition
                  - generic [ref=e731]:
                    - term [ref=e732]: Consignor
                    - definition
                  - generic [ref=e733]:
                    - term [ref=e734]: Status
                    - definition [ref=e735]:
                      - strong [ref=e736]: Draft
                - generic [ref=e737]:
                  - generic [ref=e738]:
                    - term [ref=e739]: Date created
                    - definition [ref=e740]: 7 Oct 2026
                  - generic [ref=e741]:
                    - term [ref=e742]: Date submitted
                    - definition
            - generic [ref=e743]:
              - generic [ref=e744]:
                - heading "GBN-AG-26-VS87WX" [level=3] [ref=e745]
                - list [ref=e746]:
                  - listitem [ref=e747]:
                    - link "Resume notification GBN-AG-26-VS87WX" [ref=e748] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-VS87WX
                      - text: Resume
                      - generic [ref=e749]: notification GBN-AG-26-VS87WX
                  - listitem [ref=e750]:
                    - button "Copy as new notification GBN-AG-26-VS87WX" [ref=e752] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e753]: notification GBN-AG-26-VS87WX
                  - listitem [ref=e754]:
                    - link "Delete notification GBN-AG-26-VS87WX" [ref=e755] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-VS87WX/delete
                      - text: Delete
                      - generic [ref=e756]: notification GBN-AG-26-VS87WX
              - generic [ref=e757]:
                - generic [ref=e758]:
                  - generic [ref=e759]:
                    - term [ref=e760]: Commodity
                    - definition [ref=e761]: Cow
                  - generic [ref=e762]:
                    - term [ref=e763]: Origin
                    - definition [ref=e764]: France
                  - generic [ref=e765]:
                    - term [ref=e766]: Arrival at destination
                    - definition
                - generic [ref=e767]:
                  - generic [ref=e768]:
                    - term [ref=e769]: Consignee
                    - definition [ref=e770]: British Livestock Ltd
                  - generic [ref=e771]:
                    - term [ref=e772]: Consignor
                    - definition [ref=e773]: Astra Rosales
                  - generic [ref=e774]:
                    - term [ref=e775]: Status
                    - definition [ref=e776]:
                      - strong [ref=e777]: Draft
                - generic [ref=e778]:
                  - generic [ref=e779]:
                    - term [ref=e780]: Date created
                    - definition [ref=e781]: 7 Oct 2026
                  - generic [ref=e782]:
                    - term [ref=e783]: Date submitted
                    - definition
            - generic [ref=e784]:
              - generic [ref=e785]:
                - heading "GBN-AG-26-DC4DPP" [level=3] [ref=e786]
                - list [ref=e787]:
                  - listitem [ref=e788]:
                    - link "Resume notification GBN-AG-26-DC4DPP" [ref=e789] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-DC4DPP
                      - text: Resume
                      - generic [ref=e790]: notification GBN-AG-26-DC4DPP
                  - listitem [ref=e791]:
                    - button "Copy as new notification GBN-AG-26-DC4DPP" [ref=e793] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e794]: notification GBN-AG-26-DC4DPP
                  - listitem [ref=e795]:
                    - link "Delete notification GBN-AG-26-DC4DPP" [ref=e796] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-DC4DPP/delete
                      - text: Delete
                      - generic [ref=e797]: notification GBN-AG-26-DC4DPP
              - generic [ref=e798]:
                - generic [ref=e799]:
                  - generic [ref=e800]:
                    - term [ref=e801]: Commodity
                    - definition [ref=e802]: Cow
                  - generic [ref=e803]:
                    - term [ref=e804]: Origin
                    - definition [ref=e805]: France
                  - generic [ref=e806]:
                    - term [ref=e807]: Arrival at destination
                    - definition
                - generic [ref=e808]:
                  - generic [ref=e809]:
                    - term [ref=e810]: Consignee
                    - definition
                  - generic [ref=e811]:
                    - term [ref=e812]: Consignor
                    - definition [ref=e813]: Handshake Farm 1791369255344
                  - generic [ref=e814]:
                    - term [ref=e815]: Status
                    - definition [ref=e816]:
                      - strong [ref=e817]: Draft
                - generic [ref=e818]:
                  - generic [ref=e819]:
                    - term [ref=e820]: Date created
                    - definition [ref=e821]: 7 Oct 2026
                  - generic [ref=e822]:
                    - term [ref=e823]: Date submitted
                    - definition
            - generic [ref=e824]:
              - generic [ref=e825]:
                - heading "GBN-AG-26-Q7MJ0F" [level=3] [ref=e826]
                - list [ref=e827]:
                  - listitem [ref=e828]:
                    - link "Resume notification GBN-AG-26-Q7MJ0F" [ref=e829] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-Q7MJ0F
                      - text: Resume
                      - generic [ref=e830]: notification GBN-AG-26-Q7MJ0F
                  - listitem [ref=e831]:
                    - button "Copy as new notification GBN-AG-26-Q7MJ0F" [ref=e833] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e834]: notification GBN-AG-26-Q7MJ0F
                  - listitem [ref=e835]:
                    - link "Delete notification GBN-AG-26-Q7MJ0F" [ref=e836] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-Q7MJ0F/delete
                      - text: Delete
                      - generic [ref=e837]: notification GBN-AG-26-Q7MJ0F
              - generic [ref=e838]:
                - generic [ref=e839]:
                  - generic [ref=e840]:
                    - term [ref=e841]: Commodity
                    - definition [ref=e842]: Cow
                  - generic [ref=e843]:
                    - term [ref=e844]: Origin
                    - definition [ref=e845]: France
                  - generic [ref=e846]:
                    - term [ref=e847]: Arrival at destination
                    - definition
                - generic [ref=e848]:
                  - generic [ref=e849]:
                    - term [ref=e850]: Consignee
                    - definition [ref=e851]: British Livestock Ltd
                  - generic [ref=e852]:
                    - term [ref=e853]: Consignor
                    - definition [ref=e854]: Astra Rosales
                  - generic [ref=e855]:
                    - term [ref=e856]: Status
                    - definition [ref=e857]:
                      - strong [ref=e858]: Draft
                - generic [ref=e859]:
                  - generic [ref=e860]:
                    - term [ref=e861]: Date created
                    - definition [ref=e862]: 7 Oct 2026
                  - generic [ref=e863]:
                    - term [ref=e864]: Date submitted
                    - definition
            - generic [ref=e865]:
              - generic [ref=e866]:
                - heading "GBN-AG-26-615WAY" [level=3] [ref=e867]
                - list [ref=e868]:
                  - listitem [ref=e869]:
                    - link "Resume notification GBN-AG-26-615WAY" [ref=e870] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-615WAY
                      - text: Resume
                      - generic [ref=e871]: notification GBN-AG-26-615WAY
                  - listitem [ref=e872]:
                    - button "Copy as new notification GBN-AG-26-615WAY" [ref=e874] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e875]: notification GBN-AG-26-615WAY
                  - listitem [ref=e876]:
                    - link "Delete notification GBN-AG-26-615WAY" [ref=e877] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-615WAY/delete
                      - text: Delete
                      - generic [ref=e878]: notification GBN-AG-26-615WAY
              - generic [ref=e879]:
                - generic [ref=e880]:
                  - generic [ref=e881]:
                    - term [ref=e882]: Commodity
                    - definition [ref=e883]: Cow
                  - generic [ref=e884]:
                    - term [ref=e885]: Origin
                    - definition [ref=e886]: France
                  - generic [ref=e887]:
                    - term [ref=e888]: Arrival at destination
                    - definition
                - generic [ref=e889]:
                  - generic [ref=e890]:
                    - term [ref=e891]: Consignee
                    - definition
                  - generic [ref=e892]:
                    - term [ref=e893]: Consignor
                    - definition
                  - generic [ref=e894]:
                    - term [ref=e895]: Status
                    - definition [ref=e896]:
                      - strong [ref=e897]: Draft
                - generic [ref=e898]:
                  - generic [ref=e899]:
                    - term [ref=e900]: Date created
                    - definition [ref=e901]: 7 Oct 2026
                  - generic [ref=e902]:
                    - term [ref=e903]: Date submitted
                    - definition
            - generic [ref=e904]:
              - generic [ref=e905]:
                - heading "GBN-AG-26-CP0FJ8" [level=3] [ref=e906]
                - list [ref=e907]:
                  - listitem [ref=e908]:
                    - link "Resume notification GBN-AG-26-CP0FJ8" [ref=e909] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-CP0FJ8
                      - text: Resume
                      - generic [ref=e910]: notification GBN-AG-26-CP0FJ8
                  - listitem [ref=e911]:
                    - button "Copy as new notification GBN-AG-26-CP0FJ8" [ref=e913] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e914]: notification GBN-AG-26-CP0FJ8
                  - listitem [ref=e915]:
                    - link "Delete notification GBN-AG-26-CP0FJ8" [ref=e916] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-CP0FJ8/delete
                      - text: Delete
                      - generic [ref=e917]: notification GBN-AG-26-CP0FJ8
              - generic [ref=e918]:
                - generic [ref=e919]:
                  - generic [ref=e920]:
                    - term [ref=e921]: Commodity
                    - definition [ref=e922]: Cow
                  - generic [ref=e923]:
                    - term [ref=e924]: Origin
                    - definition [ref=e925]: France
                  - generic [ref=e926]:
                    - term [ref=e927]: Arrival at destination
                    - definition
                - generic [ref=e928]:
                  - generic [ref=e929]:
                    - term [ref=e930]: Consignee
                    - definition
                  - generic [ref=e931]:
                    - term [ref=e932]: Consignor
                    - definition
                  - generic [ref=e933]:
                    - term [ref=e934]: Status
                    - definition [ref=e935]:
                      - strong [ref=e936]: Draft
                - generic [ref=e937]:
                  - generic [ref=e938]:
                    - term [ref=e939]: Date created
                    - definition [ref=e940]: 7 Oct 2026
                  - generic [ref=e941]:
                    - term [ref=e942]: Date submitted
                    - definition
            - generic [ref=e943]:
              - generic [ref=e944]:
                - heading "GBN-AG-26-HVPXNE" [level=3] [ref=e945]
                - list [ref=e946]:
                  - listitem [ref=e947]:
                    - link "Resume notification GBN-AG-26-HVPXNE" [ref=e948] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-HVPXNE
                      - text: Resume
                      - generic [ref=e949]: notification GBN-AG-26-HVPXNE
                  - listitem [ref=e950]:
                    - button "Copy as new notification GBN-AG-26-HVPXNE" [ref=e952] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e953]: notification GBN-AG-26-HVPXNE
                  - listitem [ref=e954]:
                    - link "Delete notification GBN-AG-26-HVPXNE" [ref=e955] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-HVPXNE/delete
                      - text: Delete
                      - generic [ref=e956]: notification GBN-AG-26-HVPXNE
              - generic [ref=e957]:
                - generic [ref=e958]:
                  - generic [ref=e959]:
                    - term [ref=e960]: Commodity
                    - definition [ref=e961]: Cow
                  - generic [ref=e962]:
                    - term [ref=e963]: Origin
                    - definition [ref=e964]: France
                  - generic [ref=e965]:
                    - term [ref=e966]: Arrival at destination
                    - definition
                - generic [ref=e967]:
                  - generic [ref=e968]:
                    - term [ref=e969]: Consignee
                    - definition
                  - generic [ref=e970]:
                    - term [ref=e971]: Consignor
                    - definition
                  - generic [ref=e972]:
                    - term [ref=e973]: Status
                    - definition [ref=e974]:
                      - strong [ref=e975]: Draft
                - generic [ref=e976]:
                  - generic [ref=e977]:
                    - term [ref=e978]: Date created
                    - definition [ref=e979]: 7 Oct 2026
                  - generic [ref=e980]:
                    - term [ref=e981]: Date submitted
                    - definition
            - generic [ref=e982]:
              - generic [ref=e983]:
                - heading "GBN-AG-26-TFM83W" [level=3] [ref=e984]
                - list [ref=e985]:
                  - listitem [ref=e986]:
                    - link "Resume notification GBN-AG-26-TFM83W" [ref=e987] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-TFM83W
                      - text: Resume
                      - generic [ref=e988]: notification GBN-AG-26-TFM83W
                  - listitem [ref=e989]:
                    - button "Copy as new notification GBN-AG-26-TFM83W" [ref=e991] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e992]: notification GBN-AG-26-TFM83W
                  - listitem [ref=e993]:
                    - link "Delete notification GBN-AG-26-TFM83W" [ref=e994] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-TFM83W/delete
                      - text: Delete
                      - generic [ref=e995]: notification GBN-AG-26-TFM83W
              - generic [ref=e996]:
                - generic [ref=e997]:
                  - generic [ref=e998]:
                    - term [ref=e999]: Commodity
                    - definition [ref=e1000]: Cow
                  - generic [ref=e1001]:
                    - term [ref=e1002]: Origin
                    - definition [ref=e1003]: France
                  - generic [ref=e1004]:
                    - term [ref=e1005]: Arrival at destination
                    - definition
                - generic [ref=e1006]:
                  - generic [ref=e1007]:
                    - term [ref=e1008]: Consignee
                    - definition [ref=e1009]: British Livestock Ltd
                  - generic [ref=e1010]:
                    - term [ref=e1011]: Consignor
                    - definition [ref=e1012]: Astra Rosales
                  - generic [ref=e1013]:
                    - term [ref=e1014]: Status
                    - definition [ref=e1015]:
                      - strong [ref=e1016]: Draft
                - generic [ref=e1017]:
                  - generic [ref=e1018]:
                    - term [ref=e1019]: Date created
                    - definition [ref=e1020]: 7 Oct 2026
                  - generic [ref=e1021]:
                    - term [ref=e1022]: Date submitted
                    - definition
            - generic [ref=e1023]:
              - generic [ref=e1024]:
                - heading "GBN-AG-26-RRYB2P" [level=3] [ref=e1025]
                - list [ref=e1026]:
                  - listitem [ref=e1027]:
                    - link "Resume notification GBN-AG-26-RRYB2P" [ref=e1028] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-RRYB2P
                      - text: Resume
                      - generic [ref=e1029]: notification GBN-AG-26-RRYB2P
                  - listitem [ref=e1030]:
                    - button "Copy as new notification GBN-AG-26-RRYB2P" [ref=e1032] [cursor=pointer]:
                      - text: Copy as new
                      - generic [ref=e1033]: notification GBN-AG-26-RRYB2P
                  - listitem [ref=e1034]:
                    - link "Delete notification GBN-AG-26-RRYB2P" [ref=e1035] [cursor=pointer]:
                      - /url: /live-animals/notifications/GBN-AG-26-RRYB2P/delete
                      - text: Delete
                      - generic [ref=e1036]: notification GBN-AG-26-RRYB2P
              - generic [ref=e1037]:
                - generic [ref=e1038]:
                  - generic [ref=e1039]:
                    - term [ref=e1040]: Commodity
                    - definition [ref=e1041]: Cow
                  - generic [ref=e1042]:
                    - term [ref=e1043]: Origin
                    - definition [ref=e1044]: France
                  - generic [ref=e1045]:
                    - term [ref=e1046]: Arrival at destination
                    - definition
                - generic [ref=e1047]:
                  - generic [ref=e1048]:
                    - term [ref=e1049]: Consignee
                    - definition
                  - generic [ref=e1050]:
                    - term [ref=e1051]: Consignor
                    - definition
                  - generic [ref=e1052]:
                    - term [ref=e1053]: Status
                    - definition [ref=e1054]:
                      - strong [ref=e1055]: Draft
                - generic [ref=e1056]:
                  - generic [ref=e1057]:
                    - term [ref=e1058]: Date created
                    - definition [ref=e1059]: 7 Oct 2026
                  - generic [ref=e1060]:
                    - term [ref=e1061]: Date submitted
                    - definition
            - navigation "Pagination" [ref=e1062]:
              - link "Next" [ref=e1064] [cursor=pointer]:
                - /url: /live-animals?page=2
  - contentinfo [ref=e1067]:
    - generic [ref=e1080]:
      - generic [ref=e1081]:
        - heading "Support links" [level=2] [ref=e1082]
        - list [ref=e1083]:
          - listitem [ref=e1084]:
            - link "Privacy" [ref=e1085] [cursor=pointer]:
              - /url: https://www.gov.uk/help/privacy-notice
          - listitem [ref=e1086]:
            - link "Cookies" [ref=e1087] [cursor=pointer]:
              - /url: https://www.gov.uk/help/cookies
          - listitem [ref=e1088]:
            - link "Accessibility statement" [ref=e1089] [cursor=pointer]:
              - /url: https://www.gov.uk/help/accessibility-statement
        - generic [ref=e1092]:
          - text: All content is available under the
          - link "Open Government Licence v3.0" [ref=e1093] [cursor=pointer]:
            - /url: https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/
          - text: ", except where otherwise stated"
      - link "© Crown copyright" [ref=e1095] [cursor=pointer]:
        - /url: https://www.nationalarchives.gov.uk/information-management/re-using-public-sector-information/uk-government-licensing-framework/crown-copyright/
```

# Test source

```ts
  1  | import type { APIRequestContext, APIResponse } from '@playwright/test';
  2  | import { SET_BASES } from '@page-objects/shared/sets';
  3  | 
  4  | /** A page's form fields. An array posts the key repeatedly, the way a checkbox group does. */
  5  | export type FormFields = Record<string, string | string[]>;
  6  | 
  7  | /**
  8  |  * The page the crumb cookie is minted from. No set is served at the root any
  9  |  * more — `/` is a server-wide 302 to the default set — so land on the set's own
  10 |  * dashboard. The seed context is always the animals frontend
  11 |  * (`createFrontendSeedContext`), so this set base is the right one.
  12 |  */
  13 | const CRUMB_MINT_PATH = SET_BASES.liveAnimals;
  14 | 
  15 | export class FrontendFormError extends Error {
  16 |   constructor(
  17 |     readonly status: number,
  18 |     readonly path: string,
  19 |     readonly responseBody: string,
  20 |   ) {
  21 |     super(`POST ${path} responded ${status} instead of a redirect. Body:\n${responseBody}`);
  22 |     this.name = 'FrontendFormError';
  23 |   }
  24 | }
  25 | 
  26 | const HTTP_STATUS_OK = 200;
  27 | const HTTP_STATUS_MULTIPLE_CHOICES = 300;
  28 | const HTTP_STATUS_BAD_REQUEST = 400;
  29 | 
  30 | const BODY_EXCERPT_LENGTH = 2000;
  31 | 
  32 | const isRedirect = (response: APIResponse): boolean =>
  33 |   response.status() >= HTTP_STATUS_MULTIPLE_CHOICES && response.status() < HTTP_STATUS_BAD_REQUEST;
  34 | 
  35 | const toValueList = (values: string | string[]): string[] => (Array.isArray(values) ? values : [values]);
  36 | 
  37 | const encodeFormBody = (fields: FormFields): string => {
  38 |   const pairs = Object.entries(fields).flatMap(([name, values]) => toValueList(values).map((value) => [name, value]));
  39 |   return new URLSearchParams(pairs).toString();
  40 | };
  41 | 
  42 | export class FrontendFormClient {
  43 |   private crumb: string | undefined;
  44 | 
  45 |   constructor(private readonly request: APIRequestContext) {}
  46 | 
  47 |   // @hapi/crumb (non-restful mode) matches the posted "crumb" field against the "crumb" cookie — the two names below must stay identical.
  48 |   private async crumbToken(): Promise<string> {
  49 |     if (this.crumb) {
  50 |       return this.crumb;
  51 |     }
  52 | 
  53 |     const landing = await this.request.get(CRUMB_MINT_PATH, { maxRedirects: 0 });
  54 |     if (landing.status() !== HTTP_STATUS_OK) {
  55 |       throw new Error(
  56 |         `GET ${CRUMB_MINT_PATH} answered ${landing.status()} (${landing.headers().location ?? 'no Location'}) instead of the dashboard. ` +
  57 |           'The seed context is not signed in to the frontend, so no post would reach a page.',
  58 |       );
  59 |     }
  60 | 
  61 |     const { cookies } = await this.request.storageState();
  62 |     const minted = cookies.find((cookie) => cookie.name === 'crumb')?.value;
  63 |     if (!minted) {
  64 |       throw new Error(`GET ${CRUMB_MINT_PATH} minted no "crumb" cookie, so no form post can pass CSRF validation.`);
  65 |     }
  66 | 
  67 |     this.crumb = minted;
  68 |     return minted;
  69 |   }
  70 | 
  71 |   // Pass redirectsTo for any transition (amend, cancel, delete): a refusal redirects too, just elsewhere, so 3xx alone reads it as success.
  72 |   async postForm(path: string, fields: FormFields = {}, { redirectsTo }: { redirectsTo?: RegExp } = {}): Promise<string> {
  73 |     const response = await this.request.post(path, {
  74 |       headers: { 'content-type': 'application/x-www-form-urlencoded' },
  75 |       data: encodeFormBody({ ...fields, crumb: await this.crumbToken() }),
  76 |       maxRedirects: 0,
  77 |     });
  78 | 
  79 |     // A page that rejects its payload can re-render 200 with an error summary, so a non-redirect is a failed post.
  80 |     if (!isRedirect(response)) {
> 81 |       throw new FrontendFormError(response.status(), path, (await response.text()).slice(0, BODY_EXCERPT_LENGTH));
     |             ^ FrontendFormError: POST /live-animals/notifications/GBN-AG-26-ZXNMWA/port-of-entry responded 500 instead of a redirect. Body:
  82 |     }
  83 | 
  84 |     const location = response.headers().location;
  85 |     if (!location) {
  86 |       throw new Error(`POST ${path} redirected with no Location header, so there is no next page to follow.`);
  87 |     }
  88 |     if (redirectsTo && !redirectsTo.test(location)) {
  89 |       throw new Error(`POST ${path} redirected to "${location}", not to ${redirectsTo}. The frontend refused what was asked of it.`);
  90 |     }
  91 |     return location;
  92 |   }
  93 | }
  94 | 
```