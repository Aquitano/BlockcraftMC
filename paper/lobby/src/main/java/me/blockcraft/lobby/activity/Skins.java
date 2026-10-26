package me.blockcraft.lobby.activity;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;

/** The signed skins of the lobby's figures, taken from MineSkin; a figure without a signed skin shows as Steve. */
final class Skins {

    static final ResolvableProfile SANTA = ResolvableProfile.resolvableProfile()
            .name("Santa")
            .addProperty(new ProfileProperty("textures",
                    "ewogICJ0aW1lc3RhbXAiIDogMTYxNzg4NTA1NTM5MSwKICAicHJvZmlsZUlkIiA6ICJjN2ZkZGI5YWFkZmQ0YmUzYjMyNmQ3MDBkZjI1MmU4OC"
                            + "IsCiAgInByb2ZpbGVOYW1lIiA6ICJTYW50YSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJT"
                            + "S0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zZjg2NjgyMWJmYmY1MWMwNWZmNz"
                            + "k4YWYxMDk1NzVhZmZhMThjNzc5MTUxODEwYjUyZTk5YzU0N2MzMzc5ZWI3IgogICAgfQogIH0KfQ==",
                    "CGqEqHjnV96lvUfVkWc90BC0F+pp5a4pT4QUoKQCtSenomZxTZVEWdIqLs/LIUM3OKFDFBfrWpjD8bi3vCsNwYE9OMhcSmDz2QtzJYKncfV7EM"
                            + "iEPP/aS1HF7RtUNrZx/5L9efkeFK1TvmnU45VGOGgfZPH1eerzvcrH+4Pswd3aLNwsivqar9zvOGD1ns/+Wlz5EB7pDiT+RUfSehhTp6xCiXKx"
                            + "ikK7i+ia/G33dK5YTe3928jbdltZ5vZzY2UxudT1fDfvTpoh69XbBcZmCwOwSQlu9iN/IYdInY0nEGRc8Af49mxE8rBrLO0d/G5nmkJ9Vcibh4"
                            + "ieQAqMahN9kb0p50UvMWy6J02n+hYvvnnVpdU5bbbB8lelKBIB4YZhhf6jE+KQ/aRf9roULhAZ/NYYzdKU3NKKq7dOI+y3tDxGrVdhNiepX0H0"
                            + "IjfW1+AMGzLUkirr14ATqTPno36kqFyW3QbkMLFv5H2v10Y6JazzwIDuxPWcZBv+3KVx5MyNMcDgQF5TK/qKZCPo+BH5oCE03mwtS8tD0g4tom"
                            + "kcCuA6+u0c76y3u7jlsqOxBzo4ZO3dk6L/2yuSzSmUSrScaHIUJFV/IdNOEMuA0zh5R/Vs3517iM6Lm8Q+KcQO8S0JBD0RW0U/Lo3QdFNBtG/q"
                            + "VHAysmqHwIumxo+hiMNHJos="))
                        .build();

    static final ResolvableProfile HEROBRINE = ResolvableProfile.resolvableProfile()
            .name("Herobrine")
            .addProperty(new ProfileProperty("textures",
                    "ewogICJ0aW1lc3RhbXAiIDogMTYxNzM1ODYzNTk0OCwKICAicHJvZmlsZUlkIiA6ICI1ZWE0ODg2NTg2OWI0Y2ZhOWRjNTg5YmFlZWQwNzM5MC"
                            + "IsCiAgInByb2ZpbGVOYW1lIiA6ICJfUllOMF8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAi"
                            + "U0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjYyNTBmYWRkNWEyNWNiNTNjYW"
                            + "Q0MzRjOWU4ZjgzNmYyMmMyMTQ5NTY1OWY4MDMzODlmZjA3MTQyM2E1ODAzZSIKICAgIH0KICB9Cn0=",
                    "nYaWDYE0Ifo5/ulhD7lcw6NHbV8vRhDn+XVXA/aNTl0NZHO94fE3mAu8c4B3BLwAGAm76V77pyoQEQqlKDvNv+jjeSFHf6JQ/p+ytrT856TYfV"
                            + "3Uv/f1mZZiqSpzdIWi9rq/9MOGZ6zZ17v+PdKUc/O+IGFJdJ6LgNtWXF5u8g6yC5kdXbCx3FPn4f6lygXGbdNDjAccK7RuXDdl8aDxiAVm0I+i"
                            + "mOxNjuvyM4Y0iRmn19iPMJorTcPHmju/UZfSYVWCs71ohRZc9jsmQ10qzkIv4WmSD/umyZDEi/ePW5XNTdn+hU1UGJHzR3aq2cRECB1rY1aV2G"
                            + "9WODPEkjNJ2h8ySO5ytvDlrUGO6neD22UQg3qe5bo39FzIqhgR5RNkhrPaykNJT5KjvRO0ANaQ9GufBONE0nKGRpTmqa23ipDv1qK71pPE/2zb"
                            + "mQWK9vejbxI7I6zdCLQ0PXMH2uuaolcoaS4WrYJU2u9ochslE5uaNBIWEj2pcG1rVygrRYHMc2lyjWkTcvGXwe6ioVI4Ta2eY3VXtFpeC7ywNe"
                            + "W2qAJK4wFXhh338Xpmlj+huX4DNVeZoENkIzj4SHkR8lOc0AnjxfQUuoQ/HLmc2uO0pkd2BKBelRCiTt6ZrjnH+GjjNot3589/SHLkeDnZuJqd"
                            + "EmI4c2auIyz7Kihj3jHN3GI="))
            .build();

    static final ResolvableProfile PRISONER = ResolvableProfile.resolvableProfile()
            .name("Gefangener")
            .addProperty(new ProfileProperty("textures",
                    "ewogICJ0aW1lc3RhbXAiIDogMTYxNDUzNjQ2Njg4MiwKICAicHJvZmlsZUlkIiA6ICJhNzdkNmQ2YmFjOWE0NzY3YTFhNzU1NjYxOTllYmY5Mi"
                            + "IsCiAgInByb2ZpbGVOYW1lIiA6ICIwOEJFRDUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAi"
                            + "U0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTUxYTQyN2UyYzk4MzczNzhiNj"
                            + "kzYjNiNzMzNWE3MGQ2M2NlMGRiYmZjYmYxOGRlNDVmNzIwMDYyOTA0OGQ1MSIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVs"
                            + "IiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
                    "AmZeQZUY4gmRK9xRC3uAHVHddD5UlpLRvSXEJB9bmLvDF3PMxh7QVWQoqGAgJLTdj5mszm8t+6dY/t96yPWPxNGmjSEuq+KprONmtnIHct79bi"
                            + "RPBLbEexWhb/ZS93NJRUovfU4AYRDBdj2lkLeDpW4kG5LoObfqSBAz7u9fMgF/yDfs9A6+CoXfb8OjaurmVGTlas44VfFu+x3GFPaAZetBWtAj"
                            + "ijJDxkFYpRuOehCr97H/3QheWmBzqjaY8FnZh5h884mzXmfptUcSmcKz1QbfXr6tp6dQdeB3IHM90EFOS6XWOrPshBndi76PO8iRB2cB1EyNbM"
                            + "oA/lpnK/qfqWs1re81jpnqQ9iD1JtaJM7p/4WWC9s3/iAA3tPn1g9Bruc5Wi/OSlewcDBy+YAJZNnb/TNi5T0stG/shmVizYd2UHQkuv9dDuiO"
                            + "Ae6cpXczL7dK2VyJ6ksMdonsYVzrBO7RA1KV8I+AQ9+gwuGd/cvudOUz2+ZoBcGiCWafvg9xsHX/imW3RSfqhXdgMocgLMRqV4IXbpzoLsMJx1"
                            + "EgwKHwlKFJZy1TgzT6So7gO1thhOpfR56JBxdGRpb5rKurAAJjajZ6w4RzqkEVjJvDhrP45nq2BoGIjk65szMbU0ktckYdy/qSPKwiavKUWAtY"
                            + "8Iku51R9Cg2fnOuyq4vbfw4="))
            .build();

    private Skins() {
    }
}
