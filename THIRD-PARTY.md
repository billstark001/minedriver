# Third-party notices

MineDriver's source is MIT licensed under [LICENSE](LICENSE). The standalone agent embeds relocated **ASM 9.8** and **Gson 2.13.2**; Gson is also a protocol/CLI runtime dependency.

| Component | License | Included notice |
| --- | --- | --- |
| ASM | BSD-3-Clause | [Full text](licenses/ASM-BSD-3-Clause.txt), from the [ASM project](https://asm.ow2.io/license.html). |
| Gson | Apache-2.0 | [Full text](licenses/GSON-APACHE-2.0.txt), from the [2.13.2 source tag](https://github.com/google/gson/blob/gson-parent-2.13.2/LICENSE). |
| Gradle wrapper/generated launch scripts | Apache-2.0 | Original file headers retained; [Gradle licensing](https://github.com/gradle/gradle/blob/master/LICENSE). |

JAR resources include MineDriver's license and dependency notices under `META-INF/minedriver/`. Java test doubles and native integration test-subject metadata are not embedded in the plugin/agent. Minecraft binaries, assets, loader libraries and mod dependencies are downloaded by the consuming native development build and are not redistributed in this repository.
