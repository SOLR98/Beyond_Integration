# mods/ 批量导入目录

把要一起加载的 mod jar 直接放进本目录，构建脚本会自动把它们加入
**编译 + 运行时类路径**：

```groovy
implementation fileTree(dir: 'mods', include: ['*.jar'])
```

不需要再逐个改 `build.gradle`。

- 适用场景：临时联调、测试其他 mod 与本模组的联动。
- **NeoForge 分支**（`0.8-1.21.1-neoforge`、`1.21.1-NEOFORGE`）：mod jar 使用官方映射，直接可用。
- **Forge 分支**（`0.8-1.20.1-forge`、`1.20.1-forge`）：mod jar 是 SRG 名，作为运行期 mod 直接可用；
  如果代码里要直接 `import` 其中类，请把文件名改成 `<name>-<version>.jar`，并在 `build.gradle` 里改用：

  ```groovy
  implementation fg.deobf("mods:<name>:<version>")
  ```

- 本目录默认只提交 README，`*.jar` 不进版本库（见 `.gitignore`）。
