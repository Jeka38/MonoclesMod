### Changelog

#### Version 1.9.1.13
* fix "return to ongoing call": returning to a 1:1 call from the chat/details screen used a wrong `instanceof` so the call screen opened without a connection — the hang-up button did nothing and the call could not be ended

#### Version 1.9.1.12
* collaborative SVG whiteboard per contact / room (Jingle app `urn:xmpp:jingle:apps:svg-whiteboard:0` + XEP-0284 SXE): pinch-zoom canvas, pencil/line/rectangle/ellipse brushes, width and colour; opened from the contact context menu and the 1:1 conversation menu, **shared and synchronized by default** — opening the board on one side opens it on the other end too; 1280×720 themed canvas (uniform cover scale: true circles, even brush thickness) with drawing clamped to the board, collapsible bottom-left tool rail, reset-view button, shape fill, long-press tooltips; the board can also be opened from the 1:1 call and the Muji group-call screens

#### Version 1.8.8.5
* ad-hoc commands: parse XEP-0050 allowed actions from the <actions> element; proper Next/Previous/Cancel/Finish buttons and spec-valid action values so multi-step commands (e.g. RSS transports) advance past category selection
* redesigned file downloads: HTTP auto-download and manual downloads save to the public Download folder with the original filename; downloaded files get an "Open file" button in the chat
* service management dialog: action menu derived from disco#info features and identities; falls back to all actions if disco#info fails
* service discovery: editable server field to browse any service, node-aware browsing, bold service names, back navigation one level at a time
* service management dialog: registration (XEP-0077), ad-hoc commands (XEP-0050), user search (XEP-0055), search results can be added to the roster
* service management dialog: system back button navigates one step back instead of closing to the service overview
* sort conversation list by last message option, ignoring MUC status messages
* highlight conversation rows with the account color only when all accounts are active

#### Version 1.1
* presettings correction to avoid connection problems
* some text corrections
* added optional monocles registration link
* changed appicon
* changed registration and usage for monocles users only

#### Version 1.0
* initial release
