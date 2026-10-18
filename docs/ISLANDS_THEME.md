# Islands color mapping

Light and Dark use JetBrains Islands color roles from commit
`d422387398cbecf45702238cfee4acc71bd2b97d` of
[intellij-community](https://github.com/JetBrains/intellij-community/tree/d422387398cbecf45702238cfee4acc71bd2b97d/platform/platform-resources/src/themes/islands).
The references are `ManyIslandsLight.theme.json` and `ManyIslandsDark.theme.json`.
Light is the default. Only Light and Dark palettes are supported. The compact JavaFX layout is retained.

| Application token | JetBrains role |
| --- | --- |
| window-bg, chrome, bg | main-window-bg |
| panel-bg, surface, surface-alt | tool-window-bg, editor-bg |
| input-bg, surface-sunken | editor-bg-inline |
| log-text, syntax-default | editor-text |
| text | text-default |
| text-secondary, text-muted | text-muted |
| popup-bg | popup-bg |
| brand, accent | accent-brand-bg |
| selection-bg | selection-bg-active |
| selection-inactive-bg | selection-bg-inactive |
| tab-selected-bg | tab-selected-bg-active |
| tab-selected-border | tab-selected-border-active |
| toggle-selected-bg | toolbar-selected-bg |
| toggle-selected-hover-bg | toolbar-selected-bg-hovered |

Secondary text uses the source's `text-muted` role to retain the repository's
4.5:1 text contrast requirement. Solid blue accents use white text, while pale
Light selections use black text. Dark selections use the default light text.
Canvas selection changes with focus and uses the selected theme rather than
using a separate fallback palette. Boolean SelectionColors overloads map false to Light and true to Dark.

Syntax and severity colors retain their existing semantics. Canvas and rich text
use the Islands editor background and default text. This is a color adaptation,
not a replica of Swing widgets or the Islands window geometry.

Canvas colors the entire log line using its severity foreground. Row backgrounds
remain neutral and the gutter has no severity stripe. Selected text uses the
editor foreground for readability; selection and search highlights remain visible.
DEBUG and TRACE use their dedicated level tokens.

The MainView empty state centers a content group capped at 320 px. Its 28 px icon
sits in a 44 px layout box, with a centered wrapped subtitle and a 28 px Open File
button. The group has no separate card background or border. The log panel minimum
height follows the empty state preferred height while no tabs are open, so dragging
the detail divider cannot clip its content. Opening a log restores the 120 px minimum.

## MainView islands

Recent Files, Log Viewer, and Detail use 10 px rounded clips over the workspace
background, with 6 px outer spacing and draggable dividers. Divider lines are
1 px at rest and 2 px on hover. The JavaFX SplitPane skin measures divider
thickness through preferred width in both orientations, so both dividers use
horizontal padding of 3 px. Log Viewer keeps a 120 px minimum height; Detail
keeps 80 px and Recent Files keeps a 200 px minimum width.

The rounded clips bind to actual panel dimensions and survive panel hide/show.
Dialogs keep their existing geometry.

Recent Files rows inside MainView use 6 px rounded highlights with 1 px vertical
and 2 px horizontal paint insets. Hover uses the neutral sunken surface; selection
uses active or inactive selection tokens according to tree focus. File labels and
group disclosure arrows stay readable on both fills. Empty rows remain clear,
and highlighting does not resize rows or affect standalone Recent Files views.

Adaptive tab sizing reserves 29 px per header for padding, border, and the
inter-tab gap, so rounded headers fit before overflow becomes necessary.

Log tabs use compact 28 px rounded headers with an 8 px radius and a full 1 px
outline on the selected tab. File/server icons carry the live indicator in their
upper-right corner. Native tab text, overflow, reorder, and close behavior remain
available. Unread counts use semantic error background/text tokens for contrast.
MainView context menus and native tab overflow use a 10 px rounded popup frame, 6 px inset padding, and 6 px rounded selection highlights. Selection colors follow the active theme while native keyboard navigation and overflow selection remain available.
The native overflow trigger uses a vertical three-dot SVG shape inside a 28 px hit area. Its preferred width is 25 px because TabPaneSkin adds the 3 px arrow width when laying out the button. The icon uses panel-aware chrome text and a rounded hover surface.

## Interaction states

Buttons share neutral hover and stronger pressed/armed overlays across MainView,
Preferences, File Manager, and server dialogs. Toggle selection uses the same
theme tokens in every view. Selected hover and pressed have separate fills;
Tabs have separate selected hover and
pressed tokens. These additional state colors adapt the Islands palette to JavaFX.

Disabled controls retain 0.45 opacity, ignore hover/pressed, and keep a muted
selection fill if selected. Context menus use selection background/text tokens
throughout the application. Menu bar highlights remain soft with chrome text.

Keyboard navigation enables a 1 px rounded focus ring. Mouse presses clear it.
The border is reserved while transparent and padding compensates for its width,
so keyboard focus does not resize controls. MainView uses the panel ring color;
The scene-local
focus helper installs once and does not consume navigation or activation events.

State precedence is disabled, selected pressed/armed, selected hover, selected,
pressed/armed, hover, normal. Keyboard focus adds a ring independently of fill.

Checkboxes use a flat 16 px rounded box inside a 28 px control. Unchecked boxes
use the input surface and neutral overlays; selected and mixed boxes use accent
state tokens with an on-brand mark. Keyboard focus adds a 1 px ring outside the
box without resizing it. Disabled controls retain 0.45 opacity and ignore hover
and pressed state. These rules are shared by search, preferences, and server forms.

ComboBox and Spinner share a 28 px height, 5 px corners, input surface, and a
single 1 px border. Keyboard focus changes the border without resizing the
control; the native Spinner forwards editor focus to its outer control. Spinner
editors have no separate border, and arrow buttons use neutral hover and pressed
overlays. Disabled fields retain 0.45 opacity and ignore pointer feedback.
ComboBox popups have 10 px corners, 6 px padding, and 28 px rows with 6 px corners.
Hover uses a neutral overlay, while selected rows use the active selection tokens.

## Search and preview dialogs

Rounded panel islands are exclusive to MainView. Secondary dialogs use flat
surfaces and thin separators, while buttons, inputs, and popups retain their
compact rounded control styling. Settings uses one surface across its sidebar,
content, and footer, separated by thin lines.

All secondary FXML dialogs share the simple-dialog class: About, Update,
Preferences, File Manager, Server Management, Server Edit, Find in Files, and
Log Preview. File and server sidebars, toolbars, details, and footers use the
same surface as their content. About has a compact icon/title header and a
separate action footer. Update uses a flat header and release-notes surface
instead of a gradient banner. Both use 28 px buttons.

RecentFilesPanel and LogDetailPane use embedded-panel. When loaded as scene
roots they have flat surfaces; when included in MainView the shell applies its
rounded island style and clip. The standalone Log Detail has no nested frame.

Find in Files uses flat parameter, results, and context-preview sections on one
continuous surface. Its draggable 6 px vertical divider starts at
55% results and 45% preview, with 140 px and 120 px panel minimums. The content
starts at 960 by 640 and cannot shrink below 800 by 560.

Log Preview uses a flat file header and log list, with a
compact footer. It starts at 700 by 500 with a 560 by 360 content minimum.
Window minimums account for native decorations after the stage is shown.
Buttons and search inputs are 28 px tall. Long paths use center ellipsis and
full-text tooltips; log font preferences remain intact. Empty previews show
an empty-state message instead of a stopped loading indicator.

RoundedPanelSupport applies the shared panel class and size-bound clip only to
MainView panels. Search preview selection follows focus, and target
line numbers use secondary text when unselected for contrast on target fills.

Search Results and Log Viewer each own a 10 px rounded clip. Their shared logIsland wrapper is transparent and unclipped, exposing workspace background through the 6 px draggable divider. Search Results retains its 120 px minimum width and initial 25% share; hiding it lets Log Viewer fill the wrapper with all four corners rounded.

## Legacy preference compatibility

Startup converts a stored graphite app_theme value to light once, ignoring case
and surrounding spaces. This runs during preference loading off the JavaFX
Application Thread. If saving fails, startup still uses Light and logs the failure.
Missing or unknown values resolve to Light without rewriting unrelated preferences.
The shared theme.css contains layout and control rules; complete color token sets
live only in theme-light.css and theme-dark.css. Splash generation produces embedded Light and Dark animations from their respective
theme tokens. Startup displays the matching splash after preferences are loaded,
so its first visible frame already matches the saved theme. A Swing window keeps
the animation responsive while JavaFX loads; status messages use the same palette.
Chrome text colors the progress segment, with border color for its track.
No JVM splash is configured, avoiding a fixed Light flash before a Dark startup.


## Message dialogs

All confirmation, error, warning, information and connection-progress alerts are
created through ThemedAlerts. The factory installs the current theme before show,
keeps open dialogs synchronized with theme changes, and resolves a focused owner
when the caller does not supply one. Startup errors also work without an owner.

Message dialogs use one flat surface, 20 px semantic icons, 28 px buttons and a
thin footer separator. The content starts at 480 px wide, while SSH and remote-path
errors retain 520 px and expandable technical details. Long messages wrap and
scroll in a viewport capped at 320 px or 40% of the available screen height.
The Details link uses text instead of the unthemed JavaFX bitmap graphic.
Existing confirmation results, default/cancel buttons and connection flows remain
unchanged. Password Prompt, What's New and FXML dialog layouts are separate.
