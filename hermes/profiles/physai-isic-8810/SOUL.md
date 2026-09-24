# physai-isic-8810 — 高齢者・障害者の在宅支援（ISIC 8810）を担うケア調整ロボット の physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-8810`、ISIC 8810 高齢者・障害者の入所を伴わない社会福祉）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: ケア調整ロボットが、要介護者の受付・リマインダー・補助的な支援を行う（Safeguarding Governor が gate する。高齢者のそばや家庭内での動作は人の承認が要る）。その物理的な仕事は家庭の中で、要介護者の手が届かない台所の高い棚から物を下ろすことと、部屋の間の段差スロープを倒れずに越えること。
その物理的な仕事を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:high-shelf-fetch` | manipulator | 台所の高い棚から瓶や鍋を下ろして調理台へ置く | 肩関節ピークトルク | 40 N·m（estimate） |
| `:threshold-ramp-tipover` | transport | 腕を畳んだ状態で段差スロープを下って隣の部屋へ入り、止まる（勾配を掃引） | 最小転倒余裕 | 0.30 以上（estimate） |

測定の入口: `kbb -M:dev:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:dev:physai-test`（`test-physai/care/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
この repo 自身の test は `.kotoba` で kbb では走らない（fleet の JVM gate が走らせる）。この bot の test 数は physics の test だけを数える。

## 測って分かったこと・限界（成長の第一候補）

1. **高い棚**: 肩トルクは 0.5 kg で 20.28 N·m、2 kg で 29.39 N·m、4.5 kg で 44.61 N·m。限界 40 N·m に達するのは **約 3.74 kg**。
   関節仕事は負（-24〜-50 J）: 下ろす動作なので重力が仕事をし、腕は制動している。
2. **段差スロープ**: 腕の重心 1.00 m 込みで、平地の転倒余裕 0.687、3° で 0.525、5° で 0.417、8° で 0.252、12° で 0.027。
   余裕 0.30 を割る勾配は **約 7.13°**。家庭の段差解消スロープは急なものが多いので、実測の勾配と照らす必要がある。
3. **estimate のままの値**: 肩トルク上限 40 N·m（家庭用サービスアームの仕様書）、転倒余裕 0.30、腕を畳んだときの重心 1.00 m と支持の半長 0.20 m（機体の実測）、
   制動 1.0 m/s²。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-8810 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:physai-test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-8810 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
