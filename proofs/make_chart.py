"""Builds results-chart.png from the real /eval JSON files in this folder."""
import json
from pathlib import Path

import matplotlib.pyplot as plt

here = Path(__file__).parent


def summary(name):
    return json.loads((here / name).read_text(encoding="utf-8-sig"))["summary"]


def pct(s):
    return int(s.split("(")[1].rstrip("%)"))


small = summary("eval-d1-omni-600m.json")
big = summary("eval-d1.json")

labels = ["Vector search\nonly", "+ reranker\nd1-omni:600m", "+ reranker\nd1 (2.7B)"]
top1 = [pct(big["baseline_top1"]), pct(small["reranked_top1"]), pct(big["reranked_top1"])]
top3 = [pct(big["baseline_top3"]), pct(small["reranked_top3"]), pct(big["reranked_top3"])]
secs = [big["baseline_avg_ms"] / 1000, small["reranked_avg_ms"] / 1000, big["reranked_avg_ms"] / 1000]

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 5), gridspec_kw={"width_ratios": [3, 2]})
x = range(len(labels))
w = 0.38
b1 = ax1.bar([i - w / 2 for i in x], top1, w, label="Top-1 correct", color="#2E7D5B")
b3 = ax1.bar([i + w / 2 for i in x], top3, w, label="Top-3 correct", color="#9CCFB4")
ax1.bar_label(b1, fmt="%d%%")
ax1.bar_label(b3, fmt="%d%%")
ax1.set_xticks(list(x), labels)
ax1.set_ylim(0, 112)
ax1.set_ylabel("Retrieval accuracy (20 questions)")
ax1.set_title("Did the right passage reach the LLM?")
ax1.legend(loc="upper left", frameon=False)

b = ax2.bar(labels, secs, color=["#9CCFB4", "#E0A458", "#C0392B"])
ax2.bar_label(b, labels=[f"{s:.1f}s" for s in secs])
ax2.set_ylabel("Avg retrieval time per question (s)")
ax2.set_title("Cost: time (CPU only, i7-12700H)")
ax2.set_ylim(0, max(secs) * 1.2)

for ax in (ax1, ax2):
    ax.spines[["top", "right"]].set_visible(False)

fig.suptitle("Spring AI 2.1 DecisionDocumentReranker on a local RAG app (Ollama, pgvector)", fontsize=12)
fig.tight_layout()
fig.savefig(here / "results-chart.png", dpi=160)
print("saved", here / "results-chart.png")
