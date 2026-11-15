The three wallpapers are original illustrations drawn for this theme by `make_wallpapers.py`: Viet Nam's national
flag (red field #DA251D, gold five-pointed star #FFCD00, 2:3, the star's points on a circle with a radius of one fifth
of the flag's length) waving on the left, and a map of Viet Nam on the right with the Hoang Sa (Paracel) and Truong Sa
(Spratly) archipelagos, labelled "Quần đảo Hoàng Sa (Việt Nam)", "Quần đảo Trường Sa (Việt Nam)" and "Biển Đông".
The drawing is ours and released CC0 1.0.

- `01-co-va-ban-do.jpg`: flag and map, for the morning, CC0 1.0
- `02-ban-do-vang.jpg`: the map in gold on deep red, with the flag's star faint on the left, for the evening, CC0 1.0
- `03-dem-co-va-ban-do.jpg`: a dark flag and a dim map, for the night, CC0 1.0

Map data, used to draw the outlines and not stored in this repository (the script downloads it at run time):

- Natural Earth, 1:10m Cultural Vectors, Admin 0 countries, Viet Nam point of view
  (`ne_10m_admin_0_countries_vnm.geojson`): the mainland, the coastal islands (Phu Quoc, Con Dao and others) and the
  islands of both archipelagos. Public domain: https://www.naturalearthdata.com/about/terms-of-use/
- Natural Earth, 1:10m Physical Vectors, minor islands (`ne_10m_minor_islands.geojson`): extra island points, only
  those inside the two archipelagos' boxes. Public domain.
- Downloaded from https://github.com/nvkelso/natural-earth-vector (`geojson/`).

Thirty main named features of the two archipelagos are added as dots at approximate positions (to about 0.01 degree;
list in `ISLANDS` in `make_wallpapers.py`), so each group is complete where Natural Earth has no shape for a feature.
21 of them lie within 0.05 degree of a Natural Earth shape or point. Of the other nine, eight (Truong Sa, An Bang, Song Tu Tay,
Song Tu Dong, Loai Ta, Sinh Ton, Chu Thap, Co May) were checked against English Wikipedia: "List of maritime features
in the Spratly Islands" (https://en.wikipedia.org/wiki/List_of_maritime_features_in_the_Spratly_Islands) and
"Fiery Cross Reef" (https://en.wikipedia.org/wiki/Fiery_Cross_Reef); Tree Island (Hoang Sa) is 0.051 degree from its
Natural Earth point. The positions are facts, not anyone's artwork; they are placed by us and carry no other licence.

Text is drawn into the pictures with Noto Sans (SIL Open Font License 1.1, falling back to DejaVu Sans); no font file
is included in the theme or the APK.

`screenshot.jpg` is a placeholder drawn by the same script until a screenshot from the TV replaces it.

Redraw them with `python3 -I make_wallpapers.py` (Pillow, curl, network for the download).

CC0 1.0: https://creativecommons.org/publicdomain/zero/1.0/
