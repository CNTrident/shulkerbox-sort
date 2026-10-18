# Third-party notices

The original Shulker Box Sort code remains Copyright (c) 2026 CNTrident, under the MIT license in `LICENSE`. The separately identified sorting adaptation below is distributed under LGPL-3.0-only; it is not relicensed as MIT.

## Item Scroller default sorting adaptation

- Upstream: Item Scroller, by masa (maruohon) and contributor Sakura-Ryoko.
- Source repository: <https://github.com/sakura-ryoko/itemscroller> (fork of <https://github.com/maruohon/itemscroller>).
- Release: `26.2-0.32.1`.
- Exact source commit: `28ee11632d9239193d84ca513aebaa9ae728f64c`.
- Sources: `src/main/java/fi/dy/masa/itemscroller/util/InventoryUtils.java` (`compareStacks`, shulker/bundle detection and default priority behavior), `util/SortingCategory.java` (default category order and first matching item group), and `config/Configs.java` (default `CATEGORY_NAME`, priority list and container options).
- Adapted file: `src/main/java/com/cntrident/shulkerboxsort/server/DefaultItemOrder.java`.
- License: **GNU Lesser General Public License, version 3 only**. Its complete text is in `LICENSES/LGPL-3.0.txt`, together with the incorporated GNU GPL version 3 text in `LICENSES/GPL-3.0.txt`.

Changes by CNTrident (2026): retain only the default sorting mode, remove configuration, MaLiLib and client references, evaluate creative-tab generators into a detached item/category index on the server, cache that index until a successful data reload, and adapt comparison to the existing global shulker-box layout planner. Server-provided translated display names replace the original client's current language. Unknown or failing third-party categories fall back to the `OTHER` category and name ordering. The pre-existing packing planner deliberately preserves the order of component-identical stacks instead of moving a partial stack solely to put it last.

This adaptation is provided without warranty, including implied warranties of merchantability or fitness for a particular purpose. See the license texts for the full terms.

Both license texts, this notice and the complete adapted Java source are included in the built JAR (`META-INF/licenses/` and `META-INF/sources/`). The full mod source is also provided in each release backup ZIP. These sources permit users to inspect, modify, rebuild and replace the adapted component; the original project places no restriction on reverse engineering for debugging such modifications.

The complete Item Scroller mod, its client inventory-click implementation and MaLiLib are not bundled or required. The Fabric Loader, Fabric API and Minecraft remain external runtime dependencies under their respective licenses.
