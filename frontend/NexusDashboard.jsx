import React, { startTransition, useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  LineChart,
  BarChart,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  Line,
  Bar,
  CartesianGrid,
  ResponsiveContainer,
  Cell,
  ScatterChart,
  Scatter,
} from "recharts";

const PATHS = {
  inventorySkew: "artifacts/demo/rl-market-maker/analysis/inventory_skew.csv",
  spreadSurface: "artifacts/demo/rl-market-maker/analysis/spread_vs_volatility.csv",
  toxicFlow: "artifacts/demo/rl-market-maker/analysis/toxic_flow_adaptation.csv",
  valueSurface: "artifacts/demo/rl-market-maker/analysis/value_function_surface.csv",
  sanityChecks: "artifacts/demo/rl-market-maker/analysis/sanity_checks.json",
  adverseSelection: "artifacts/demo/adverse-selection/adverse_selection_sweep.json",
  sessionPnl: "artifacts/demo/session/session_pnl_attribution.json",
};

const SAMPLE_RL = {
  inventorySkew: [
    { volatility_bin: 0, inventory_bin: 0, bid_offset_ticks: 1, ask_offset_ticks: 3, total_spread_ticks: 4, skew_direction: "buy_skew" },
    { volatility_bin: 0, inventory_bin: 1, bid_offset_ticks: 1, ask_offset_ticks: 2, total_spread_ticks: 3, skew_direction: "buy_skew" },
    { volatility_bin: 0, inventory_bin: 2, bid_offset_ticks: 2, ask_offset_ticks: 2, total_spread_ticks: 4, skew_direction: "symmetric" },
    { volatility_bin: 0, inventory_bin: 3, bid_offset_ticks: 2, ask_offset_ticks: 1, total_spread_ticks: 3, skew_direction: "sell_skew" },
    { volatility_bin: 0, inventory_bin: 4, bid_offset_ticks: 3, ask_offset_ticks: 1, total_spread_ticks: 4, skew_direction: "sell_skew" },
    { volatility_bin: 1, inventory_bin: 0, bid_offset_ticks: 1, ask_offset_ticks: 4, total_spread_ticks: 5, skew_direction: "buy_skew" },
    { volatility_bin: 1, inventory_bin: 1, bid_offset_ticks: 2, ask_offset_ticks: 3, total_spread_ticks: 5, skew_direction: "buy_skew" },
    { volatility_bin: 1, inventory_bin: 2, bid_offset_ticks: 3, ask_offset_ticks: 3, total_spread_ticks: 6, skew_direction: "symmetric" },
    { volatility_bin: 1, inventory_bin: 3, bid_offset_ticks: 3, ask_offset_ticks: 2, total_spread_ticks: 5, skew_direction: "sell_skew" },
    { volatility_bin: 1, inventory_bin: 4, bid_offset_ticks: 4, ask_offset_ticks: 1, total_spread_ticks: 5, skew_direction: "sell_skew" },
    { volatility_bin: 2, inventory_bin: 0, bid_offset_ticks: 2, ask_offset_ticks: 5, total_spread_ticks: 7, skew_direction: "buy_skew" },
    { volatility_bin: 2, inventory_bin: 1, bid_offset_ticks: 2, ask_offset_ticks: 4, total_spread_ticks: 6, skew_direction: "buy_skew" },
    { volatility_bin: 2, inventory_bin: 2, bid_offset_ticks: 3, ask_offset_ticks: 3, total_spread_ticks: 6, skew_direction: "symmetric" },
    { volatility_bin: 2, inventory_bin: 3, bid_offset_ticks: 4, ask_offset_ticks: 2, total_spread_ticks: 6, skew_direction: "sell_skew" },
    { volatility_bin: 2, inventory_bin: 4, bid_offset_ticks: 5, ask_offset_ticks: 2, total_spread_ticks: 7, skew_direction: "sell_skew" },
    { volatility_bin: 3, inventory_bin: 0, bid_offset_ticks: 3, ask_offset_ticks: 5, total_spread_ticks: 8, skew_direction: "buy_skew" },
    { volatility_bin: 3, inventory_bin: 1, bid_offset_ticks: 3, ask_offset_ticks: 4, total_spread_ticks: 7, skew_direction: "buy_skew" },
    { volatility_bin: 3, inventory_bin: 2, bid_offset_ticks: 4, ask_offset_ticks: 4, total_spread_ticks: 8, skew_direction: "symmetric" },
    { volatility_bin: 3, inventory_bin: 3, bid_offset_ticks: 4, ask_offset_ticks: 3, total_spread_ticks: 7, skew_direction: "sell_skew" },
    { volatility_bin: 3, inventory_bin: 4, bid_offset_ticks: 5, ask_offset_ticks: 3, total_spread_ticks: 8, skew_direction: "sell_skew" },
  ],
  spreadSurface: [
    { volatility_bin: 0, inventory_bin: 0, bid_offset_ticks: 1, ask_offset_ticks: 3, total_spread_ticks: 4, as_total_spread_estimate_ticks: 3.6, spread_minus_as_ticks: 0.4 },
    { volatility_bin: 0, inventory_bin: 2, bid_offset_ticks: 2, ask_offset_ticks: 2, total_spread_ticks: 4, as_total_spread_estimate_ticks: 3.6, spread_minus_as_ticks: 0.4 },
    { volatility_bin: 0, inventory_bin: 4, bid_offset_ticks: 3, ask_offset_ticks: 1, total_spread_ticks: 4, as_total_spread_estimate_ticks: 3.6, spread_minus_as_ticks: 0.4 },
    { volatility_bin: 1, inventory_bin: 0, bid_offset_ticks: 1, ask_offset_ticks: 4, total_spread_ticks: 5, as_total_spread_estimate_ticks: 4.8, spread_minus_as_ticks: 0.2 },
    { volatility_bin: 1, inventory_bin: 2, bid_offset_ticks: 3, ask_offset_ticks: 3, total_spread_ticks: 6, as_total_spread_estimate_ticks: 4.8, spread_minus_as_ticks: 1.2 },
    { volatility_bin: 1, inventory_bin: 4, bid_offset_ticks: 4, ask_offset_ticks: 1, total_spread_ticks: 5, as_total_spread_estimate_ticks: 4.8, spread_minus_as_ticks: 0.2 },
    { volatility_bin: 2, inventory_bin: 0, bid_offset_ticks: 2, ask_offset_ticks: 5, total_spread_ticks: 7, as_total_spread_estimate_ticks: 6.2, spread_minus_as_ticks: 0.8 },
    { volatility_bin: 2, inventory_bin: 2, bid_offset_ticks: 3, ask_offset_ticks: 3, total_spread_ticks: 6, as_total_spread_estimate_ticks: 6.2, spread_minus_as_ticks: -0.2 },
    { volatility_bin: 2, inventory_bin: 4, bid_offset_ticks: 5, ask_offset_ticks: 2, total_spread_ticks: 7, as_total_spread_estimate_ticks: 6.2, spread_minus_as_ticks: 0.8 },
    { volatility_bin: 3, inventory_bin: 0, bid_offset_ticks: 3, ask_offset_ticks: 5, total_spread_ticks: 8, as_total_spread_estimate_ticks: 7.5, spread_minus_as_ticks: 0.5 },
    { volatility_bin: 3, inventory_bin: 2, bid_offset_ticks: 4, ask_offset_ticks: 4, total_spread_ticks: 8, as_total_spread_estimate_ticks: 7.5, spread_minus_as_ticks: 0.5 },
    { volatility_bin: 3, inventory_bin: 4, bid_offset_ticks: 5, ask_offset_ticks: 3, total_spread_ticks: 8, as_total_spread_estimate_ticks: 7.5, spread_minus_as_ticks: 0.5 },
  ],
  toxicFlow: [
    { adverse_fill_ratio_bin: 0, total_spread_ticks: 2, bid_offset_ticks: 1, ask_offset_ticks: 1 },
    { adverse_fill_ratio_bin: 1, total_spread_ticks: 4, bid_offset_ticks: 2, ask_offset_ticks: 2 },
    { adverse_fill_ratio_bin: 2, total_spread_ticks: 6, bid_offset_ticks: 3, ask_offset_ticks: 3 },
    { adverse_fill_ratio_bin: 3, total_spread_ticks: 8, bid_offset_ticks: 4, ask_offset_ticks: 4 },
  ],
  valueSurface: [
    { inventory_bin: 0, time_remaining_bin: 0, max_q_value: 38 },
    { inventory_bin: 1, time_remaining_bin: 0, max_q_value: 58 },
    { inventory_bin: 2, time_remaining_bin: 0, max_q_value: 80 },
    { inventory_bin: 3, time_remaining_bin: 0, max_q_value: 56 },
    { inventory_bin: 4, time_remaining_bin: 0, max_q_value: 34 },
    { inventory_bin: 0, time_remaining_bin: 1, max_q_value: 52 },
    { inventory_bin: 1, time_remaining_bin: 1, max_q_value: 73 },
    { inventory_bin: 2, time_remaining_bin: 1, max_q_value: 95 },
    { inventory_bin: 3, time_remaining_bin: 1, max_q_value: 71 },
    { inventory_bin: 4, time_remaining_bin: 1, max_q_value: 48 },
    { inventory_bin: 0, time_remaining_bin: 2, max_q_value: 67 },
    { inventory_bin: 1, time_remaining_bin: 2, max_q_value: 88 },
    { inventory_bin: 2, time_remaining_bin: 2, max_q_value: 111 },
    { inventory_bin: 3, time_remaining_bin: 2, max_q_value: 86 },
    { inventory_bin: 4, time_remaining_bin: 2, max_q_value: 63 },
    { inventory_bin: 0, time_remaining_bin: 3, max_q_value: 82 },
    { inventory_bin: 1, time_remaining_bin: 3, max_q_value: 104 },
    { inventory_bin: 2, time_remaining_bin: 3, max_q_value: 128 },
    { inventory_bin: 3, time_remaining_bin: 3, max_q_value: 102 },
    { inventory_bin: 4, time_remaining_bin: 3, max_q_value: 79 },
  ],
  sanityChecks: {
    sanity_checks: [
      { name: "inventory_skew_detected", passed: true, detail: "yes" },
      { name: "spread_widens_in_stress", passed: true, detail: "yes" },
      { name: "toxic_flow_adaptation_detected", passed: true, detail: "yes" },
      { name: "value_surface_economically_ranked", passed: true, detail: "yes" },
    ],
  },
};

const SAMPLE_ADVERSE_SELECTION = {
  conditions: [
    { informed_fraction: 0.0, mean_quoted_spread: 2.1, mean_market_maker_pnl: 12.4, mean_kyle_lambda: 0.08, sessions: 12 },
    { informed_fraction: 0.1, mean_quoted_spread: 2.8, mean_market_maker_pnl: 8.2, mean_kyle_lambda: 0.12, sessions: 12 },
    { informed_fraction: 0.2, mean_quoted_spread: 3.6, mean_market_maker_pnl: 3.1, mean_kyle_lambda: 0.17, sessions: 12 },
    { informed_fraction: 0.3, mean_quoted_spread: 4.5, mean_market_maker_pnl: -1.8, mean_kyle_lambda: 0.23, sessions: 12 },
    { informed_fraction: 0.4, mean_quoted_spread: 5.3, mean_market_maker_pnl: -6.9, mean_kyle_lambda: 0.31, sessions: 12 },
    { informed_fraction: 0.5, mean_quoted_spread: 6.2, mean_market_maker_pnl: -12.7, mean_kyle_lambda: 0.4, sessions: 12 },
  ],
};

const SAMPLE_SESSION_PNL = {
  agents: [
    { agent: "RL Market Maker", agent_type: "RLMarketMaker", total_pnl: 14.8, spread_capture: 18.9, adverse_selection_cost: -6.2, inventory_management: 2.1 },
    { agent: "AS Benchmark", agent_type: "AvellanedaStoikov", total_pnl: 9.4, spread_capture: 12.1, adverse_selection_cost: -4.8, inventory_management: 2.1 },
    { agent: "Informed Trader", agent_type: "InformedTradingAgent", total_pnl: 6.3, spread_capture: 0.0, adverse_selection_cost: 4.1, inventory_management: 2.2 },
    { agent: "Noise Trader", agent_type: "NoiseTradingAgent", total_pnl: -5.1, spread_capture: -1.1, adverse_selection_cost: -2.3, inventory_management: -1.7 },
  ],
};

const PANEL_LINKS = [
  { id: "rl-policy", label: "RL Policy" },
  { id: "adverse-selection", label: "Adverse Selection" },
  { id: "pnl-decomposition", label: "P&L Decomposition" },
];

function parseCsv(text) {
  const trimmed = text.trim();
  if (!trimmed) {
    return [];
  }
  const [headerLine, ...lines] = trimmed.split(/\r?\n/);
  const headers = headerLine.split(",").map((part) => part.trim());
  return lines
    .filter((line) => line.trim().length > 0)
    .map((line) => {
      const values = line.split(",");
      const row = {};
      headers.forEach((header, index) => {
        row[header] = coerceValue(values[index] ?? "");
      });
      return row;
    });
}

function coerceValue(rawValue) {
  const value = rawValue.trim();
  if (value === "true") {
    return true;
  }
  if (value === "false") {
    return false;
  }
  if (value !== "" && !Number.isNaN(Number(value))) {
    return Number(value);
  }
  return value;
}

async function fetchCsv(path) {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`Missing ${path}`);
  }
  return parseCsv(await response.text());
}

async function fetchJson(path) {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`Missing ${path}`);
  }
  return response.json();
}

function useArtifactLoader() {
  const [state, setState] = useState({
    rl: { data: null, source: "loading", message: "Loading policy analysis artifacts..." },
    adverseSelection: { data: null, source: "loading", message: "Loading adverse-selection sweep..." },
    sessionPnl: { data: null, source: "loading", message: "Loading session P&L attribution..." },
  });

  useEffect(() => {
    let cancelled = false;

    Promise.allSettled([
      Promise.all([
        fetchCsv(PATHS.inventorySkew),
        fetchCsv(PATHS.spreadSurface),
        fetchCsv(PATHS.toxicFlow),
        fetchCsv(PATHS.valueSurface),
        fetchJson(PATHS.sanityChecks),
      ]),
      fetchJson(PATHS.adverseSelection),
      fetchJson(PATHS.sessionPnl),
    ]).then((results) => {
      if (cancelled) {
        return;
      }

      startTransition(() => {
        const [rlResult, adverseResult, pnlResult] = results;
        setState({
          rl:
            rlResult.status === "fulfilled" && rlResult.value.every((dataset) => (Array.isArray(dataset) ? dataset.length > 0 : true))
              ? {
                  data: {
                    inventorySkew: rlResult.value[0],
                    spreadSurface: rlResult.value[1],
                    toxicFlow: rlResult.value[2],
                    valueSurface: rlResult.value[3],
                    sanityChecks: rlResult.value[4],
                  },
                  source: "artifact",
                  message: "Loaded from artifacts/demo/rl-market-maker/analysis/",
                }
              : {
                  data: SAMPLE_RL,
                  source: "sample",
                  message: "No data — run DeterministicRlDemo first. Showing embedded sample RL analysis.",
                },
          adverseSelection:
            adverseResult.status === "fulfilled" && adverseResult.value?.conditions?.length
              ? {
                  data: adverseResult.value,
                  source: "artifact",
                  message: "Loaded from artifacts/demo/adverse-selection/",
                }
              : {
                  data: SAMPLE_ADVERSE_SELECTION,
                  source: "sample",
                  message: "No data — run AdverseSelectionSweepDemo first. Showing embedded adverse-selection sweep.",
                },
          sessionPnl:
            pnlResult.status === "fulfilled" && pnlResult.value?.agents?.length
              ? {
                  data: pnlResult.value,
                  source: "artifact",
                  message: "Loaded from artifacts/demo/session/",
                }
              : {
                  data: SAMPLE_SESSION_PNL,
                  source: "sample",
                  message: "No data — run SessionPnlAttributionDemo first. Showing embedded P&L attribution sample.",
                },
        });
      });
    });

    return () => {
      cancelled = true;
    };
  }, []);

  return state;
}

function getSanityCheck(sanityChecks, name) {
  return sanityChecks?.sanity_checks?.find((entry) => entry.name === name) ?? null;
}

function heatmapCells(rows, xKey, yKey, valueKey) {
  return rows.map((row) => ({
    x: Number(row[xKey]),
    y: Number(row[yKey]),
    value: Number(row[valueKey]),
    ...row,
  }));
}

function extent(rows, key) {
  const values = rows.map((row) => Number(row[key])).filter((value) => Number.isFinite(value));
  return { min: Math.min(...values), max: Math.max(...values) };
}

function skewColor(value) {
  const clamped = Math.max(-4, Math.min(4, value));
  if (clamped === 0) {
    return "#f4f7fb";
  }
  if (clamped > 0) {
    const intensity = 90 + clamped * 30;
    return `rgb(${Math.min(255, intensity + 60)}, ${Math.max(40, 160 - clamped * 20)}, ${Math.max(40, 130 - clamped * 22)})`;
  }
  const abs = Math.abs(clamped);
  return `rgb(${Math.max(45, 150 - abs * 22)}, ${Math.max(60, 170 - abs * 12)}, ${Math.min(255, 120 + abs * 30)})`;
}

function monochromeColor(value, min, max) {
  const spread = Math.max(1, max - min);
  const ratio = (value - min) / spread;
  const shade = Math.round(220 - ratio * 150);
  return `rgb(${shade - 6}, ${shade + 4}, ${shade + 16})`;
}

function valueColor(value, min, max) {
  const spread = Math.max(1, max - min);
  const ratio = (value - min) / spread;
  const red = Math.round(60 + ratio * 110);
  const green = Math.round(90 + ratio * 100);
  const blue = Math.round(120 + ratio * 70);
  return `rgb(${red}, ${green}, ${blue})`;
}

function formatPercent(value) {
  return `${Math.round(value * 100)}%`;
}

function formatSigned(value) {
  const rounded = Number(value).toFixed(2);
  return Number(value) > 0 ? `+${rounded}` : rounded;
}

function badgeClasses(passed) {
  return passed
    ? "border-emerald-500/40 bg-emerald-500/15 text-emerald-300"
    : "border-rose-500/40 bg-rose-500/15 text-rose-300";
}

function sourceClasses(source) {
  return source === "artifact"
    ? "border-sky-500/30 bg-sky-500/10 text-sky-200"
    : "border-amber-500/30 bg-amber-500/10 text-amber-200";
}

function SquarePoint(props) {
  const { cx, cy, payload } = props;
  return <rect x={cx - 16} y={cy - 16} width={32} height={32} rx={6} fill={payload.fill} stroke="rgba(255,255,255,0.14)" />;
}

function CustomTooltip({ active, payload, labelFormatter }) {
  if (!active || !payload || !payload.length) {
    return null;
  }
  const point = payload[0].payload;
  return (
    <div className="rounded-lg border border-white/10 bg-slate-950/95 p-3 text-xs text-slate-200 shadow-2xl">
      {labelFormatter ? <div className="mb-2 font-medium text-white">{labelFormatter(point)}</div> : null}
      {Object.entries(point)
        .filter(([key]) => !["fill", "x", "y"].includes(key))
        .map(([key, value]) => (
          <div key={key} className="flex items-center justify-between gap-3">
            <span className="text-slate-400">{key}</span>
            <span className="font-mono text-slate-100">{String(value)}</span>
          </div>
        ))}
    </div>
  );
}

function SectionHeader({ kicker, title, copy, badge }) {
  return (
    <div className="mb-6 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
      <div>
        <div className="text-xs uppercase tracking-[0.25em] text-sky-300">{kicker}</div>
        <h2 className="mt-2 text-2xl font-semibold text-white">{title}</h2>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">{copy}</p>
      </div>
      {badge}
    </div>
  );
}

function StatusBadge({ label, passed }) {
  return <span className={`inline-flex rounded-full border px-3 py-1 text-xs font-medium ${badgeClasses(passed)}`}>{label}</span>;
}

function SourceNotice({ source, message }) {
  return <div className={`rounded-xl border px-4 py-3 text-sm ${sourceClasses(source)}`}>{message}</div>;
}

function MetricCard({ label, value, tone = "text-white" }) {
  return (
    <div className="rounded-2xl border border-white/8 bg-slate-900/70 p-4">
      <div className="text-xs uppercase tracking-[0.18em] text-slate-400">{label}</div>
      <div className={`mt-3 text-2xl font-semibold ${tone}`}>{value}</div>
    </div>
  );
}

export default function NexusDashboard() {
  const datasets = useArtifactLoader();
  const deferredDatasets = useDeferredValue(datasets);

  const rl = deferredDatasets.rl.data ?? SAMPLE_RL;
  const adverseSelection = deferredDatasets.adverseSelection.data ?? SAMPLE_ADVERSE_SELECTION;
  const sessionPnl = deferredDatasets.sessionPnl.data ?? SAMPLE_SESSION_PNL;

  const inventoryHeatmap = useMemo(
    () =>
      heatmapCells(
        rl.inventorySkew.map((row) => ({
          ...row,
          skew: Number(row.bid_offset_ticks) - Number(row.ask_offset_ticks),
        })),
        "inventory_bin",
        "volatility_bin",
        "skew"
      ).map((cell) => ({ ...cell, fill: skewColor(cell.value) })),
    [rl.inventorySkew]
  );

  const spreadHeatmap = useMemo(() => {
    const cells = heatmapCells(rl.spreadSurface, "volatility_bin", "inventory_bin", "total_spread_ticks");
    const range = extent(cells, "value");
    return {
      cells: cells.map((cell) => ({ ...cell, fill: monochromeColor(cell.value, range.min, range.max) })),
      range,
    };
  }, [rl.spreadSurface]);

  const valueHeatmap = useMemo(() => {
    const cells = heatmapCells(rl.valueSurface, "inventory_bin", "time_remaining_bin", "max_q_value");
    const range = extent(cells, "value");
    return {
      cells: cells.map((cell) => ({ ...cell, fill: valueColor(cell.value, range.min, range.max) })),
      range,
    };
  }, [rl.valueSurface]);

  const spreadComparison = useMemo(
    () =>
      rl.spreadSurface.map((row) => ({
        volatility_bin: row.volatility_bin,
        inventory_bin: row.inventory_bin,
        rl_spread: row.total_spread_ticks,
        as_spread: Number(row.as_total_spread_estimate_ticks ?? 0),
        spread_delta: Number(row.spread_minus_as_ticks ?? 0),
        label: `Vol ${row.volatility_bin} / Inv ${row.inventory_bin}`,
      })),
    [rl.spreadSurface]
  );

  const toxicCheck = getSanityCheck(rl.sanityChecks, "toxic_flow_adaptation_detected");
  const skewCheck = getSanityCheck(rl.sanityChecks, "inventory_skew_detected");
  const spreadCheck = getSanityCheck(rl.sanityChecks, "spread_widens_in_stress");
  const valueCheck = getSanityCheck(rl.sanityChecks, "value_surface_economically_ranked");

  const adverseSelectionSeries = useMemo(() => {
    const rows = adverseSelection.conditions ?? [];
    const breakevenIndex = rows.findIndex((row) => Number(row.mean_market_maker_pnl) <= 0);
    return rows.map((row, index) => ({
      ...row,
      informed_fraction_pct: Math.round(Number(row.informed_fraction) * 100),
      breakeven_pnl: index === breakevenIndex ? row.mean_market_maker_pnl : null,
    }));
  }, [adverseSelection]);

  const breakevenPoint = adverseSelectionSeries.find((row) => row.breakeven_pnl !== null) ?? null;
  const spreadRange = extent(adverseSelectionSeries, "mean_quoted_spread");
  const pnlRange = extent(adverseSelectionSeries, "mean_market_maker_pnl");

  const pnlRows = useMemo(
    () =>
      (sessionPnl.agents ?? []).map((row) => {
        const components = [
          { key: "spread_capture", label: "Spread capture", value: Number(row.spread_capture) },
          { key: "adverse_selection_cost", label: "Adverse selection", value: Number(row.adverse_selection_cost) },
          { key: "inventory_management", label: "Inventory", value: Number(row.inventory_management) },
        ];
        const dominant = components.reduce((best, current) =>
          Math.abs(current.value) > Math.abs(best.value) ? current : best
        );
        return {
          ...row,
          dominant_component: dominant.label,
        };
      }),
    [sessionPnl]
  );

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <div className="mx-auto max-w-7xl px-6 py-8 lg:px-10">
        <header className="sticky top-0 z-20 mb-8 rounded-3xl border border-white/10 bg-slate-950/85 px-6 py-5 backdrop-blur">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="text-xs uppercase tracking-[0.35em] text-sky-300">NEXUS</div>
              <h1 className="mt-2 text-3xl font-semibold text-white">Deterministic Market Microstructure Dashboard</h1>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">
                Static-file visualization for RL policy behavior, adverse-selection stress, and session P&amp;L decomposition.
              </p>
            </div>
            <nav className="flex flex-wrap gap-2">
              {PANEL_LINKS.map((panel) => (
                <a
                  key={panel.id}
                  href={`#${panel.id}`}
                  className="rounded-full border border-white/10 bg-slate-900/80 px-4 py-2 text-sm text-slate-200 transition hover:border-sky-400/40 hover:text-white"
                >
                  {panel.label}
                </a>
              ))}
            </nav>
          </div>
        </header>

        <section id="rl-policy" className="mb-14 rounded-[2rem] border border-sky-400/15 bg-[radial-gradient(circle_at_top_left,_rgba(56,189,248,0.18),_transparent_32%),linear-gradient(180deg,rgba(15,23,42,0.95),rgba(2,6,23,1))] p-6 shadow-[0_30px_120px_rgba(2,6,23,0.55)] lg:p-8">
          <SectionHeader
            kicker="Hero Panel"
            title="RL Policy Heatmaps"
            copy="The dashboard leads with policy structure rather than headline performance. These views answer whether the learned quoting policy behaves like a sensible market maker under inventory pressure, volatility stress, toxic flow, and finite horizon constraints."
            badge={skewCheck ? <StatusBadge label={`Inventory skew ${skewCheck.passed ? "PASS" : "FAIL"}`} passed={skewCheck.passed} /> : null}
          />
          <SourceNotice source={deferredDatasets.rl.source} message={deferredDatasets.rl.message} />

          <div className="mt-6 grid gap-6 xl:grid-cols-2">
            <article className="rounded-3xl border border-white/8 bg-slate-900/70 p-5">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-medium text-white">Inventory Skew Heatmap</h3>
                  <p className="mt-1 text-sm text-slate-400">Blue means the policy leans to buy. Red means it leans to sell.</p>
                </div>
                {skewCheck ? <StatusBadge label={skewCheck.passed ? "PASS" : "FAIL"} passed={skewCheck.passed} /> : null}
              </div>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <ScatterChart margin={{ top: 16, right: 16, bottom: 10, left: 10 }}>
                    <CartesianGrid stroke="rgba(255,255,255,0.08)" />
                    <XAxis type="number" dataKey="x" name="Inventory bin" tickCount={5} domain={[-0.5, 4.5]} stroke="#94a3b8" />
                    <YAxis type="number" dataKey="y" name="Volatility bin" tickCount={4} domain={[-0.5, 3.5]} stroke="#94a3b8" />
                    <Tooltip content={<CustomTooltip labelFormatter={(point) => `Inventory ${point.inventory_bin}, Volatility ${point.volatility_bin}`} />} />
                    <Scatter data={inventoryHeatmap} shape={<SquarePoint />}>
                      {inventoryHeatmap.map((entry, index) => (
                        <Cell key={`inventory-cell-${index}`} fill={entry.fill} />
                      ))}
                    </Scatter>
                  </ScatterChart>
                </ResponsiveContainer>
              </div>
              <p className="mt-4 text-sm text-slate-400">{skewCheck?.detail ?? "No sanity-check data available."}</p>
            </article>

            <article className="rounded-3xl border border-white/8 bg-slate-900/70 p-5">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-medium text-white">Spread vs Volatility Surface</h3>
                  <p className="mt-1 text-sm text-slate-400">Darker cells imply wider spreads under volatility and inventory stress.</p>
                </div>
                {spreadCheck ? <StatusBadge label={spreadCheck.passed ? "PASS" : "FAIL"} passed={spreadCheck.passed} /> : null}
              </div>
              <div className="grid gap-4 xl:grid-cols-[1.4fr_0.9fr]">
                <div className="h-80">
                  <ResponsiveContainer width="100%" height="100%">
                    <ScatterChart margin={{ top: 16, right: 16, bottom: 10, left: 10 }}>
                      <CartesianGrid stroke="rgba(255,255,255,0.08)" />
                      <XAxis type="number" dataKey="x" name="Volatility bin" tickCount={4} domain={[-0.5, 3.5]} stroke="#94a3b8" />
                      <YAxis type="number" dataKey="y" name="Inventory bin" tickCount={5} domain={[-0.5, 4.5]} stroke="#94a3b8" />
                      <Tooltip content={<CustomTooltip labelFormatter={(point) => `Volatility ${point.volatility_bin}, Inventory ${point.inventory_bin}`} />} />
                      <Scatter data={spreadHeatmap.cells} shape={<SquarePoint />}>
                        {spreadHeatmap.cells.map((entry, index) => (
                          <Cell key={`spread-cell-${index}`} fill={entry.fill} />
                        ))}
                      </Scatter>
                    </ScatterChart>
                  </ResponsiveContainer>
                </div>
                <div className="rounded-2xl border border-white/8 bg-slate-950/55 p-4">
                  <div className="text-sm font-medium text-white">AS Comparison Snapshot</div>
                  <p className="mt-2 text-sm leading-6 text-slate-400">
                    The side panel compares learned spreads to the analytical Avellaneda-Stoikov baseline. Positive deltas indicate the RL policy is wider than AS in that slice.
                  </p>
                  <div className="mt-4 space-y-3">
                    {spreadComparison.slice(0, 6).map((row) => (
                      <div key={`${row.volatility_bin}-${row.inventory_bin}`} className="rounded-xl border border-white/6 bg-slate-900/70 p-3">
                        <div className="text-xs uppercase tracking-[0.18em] text-slate-400">{row.label}</div>
                        <div className="mt-2 grid grid-cols-3 gap-2 text-sm">
                          <div>
                            <div className="text-slate-400">RL</div>
                            <div className="font-mono text-white">{row.rl_spread}</div>
                          </div>
                          <div>
                            <div className="text-slate-400">AS</div>
                            <div className="font-mono text-white">{row.as_spread.toFixed(2)}</div>
                          </div>
                          <div>
                            <div className="text-slate-400">Delta</div>
                            <div className={`font-mono ${row.spread_delta >= 0 ? "text-amber-300" : "text-sky-300"}`}>{formatSigned(row.spread_delta)}</div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
              <p className="mt-4 text-sm text-slate-400">{spreadCheck?.detail ?? "No sanity-check data available."}</p>
            </article>

            <article className="rounded-3xl border border-white/8 bg-slate-900/70 p-5">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-medium text-white">Toxic Flow Adaptation</h3>
                  <p className="mt-1 text-sm text-slate-400">Total spread by adverse-fill ratio bin. Wider quotes should appear as toxicity rises.</p>
                </div>
                {toxicCheck ? <StatusBadge label={toxicCheck.passed ? "PASS" : "FAIL"} passed={toxicCheck.passed} /> : null}
              </div>
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={rl.toxicFlow} margin={{ top: 10, right: 16, left: 0, bottom: 10 }}>
                    <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                    <XAxis dataKey="adverse_fill_ratio_bin" stroke="#94a3b8" />
                    <YAxis stroke="#94a3b8" />
                    <Tooltip />
                    <Bar dataKey="total_spread_ticks" radius={[6, 6, 0, 0]}>
                      {rl.toxicFlow.map((entry, index) => (
                        <Cell key={`toxic-${index}`} fill={monochromeColor(entry.total_spread_ticks, 2, 8)} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <p className="mt-4 text-sm text-slate-400">{toxicCheck?.detail ?? "No sanity-check data available."}</p>
            </article>

            <article className="rounded-3xl border border-white/8 bg-slate-900/70 p-5">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-medium text-white">Value Function Surface</h3>
                  <p className="mt-1 text-sm text-slate-400">Higher values should cluster near neutral inventory with more time remaining.</p>
                </div>
                {valueCheck ? <StatusBadge label={valueCheck.passed ? "PASS" : "FAIL"} passed={valueCheck.passed} /> : null}
              </div>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <ScatterChart margin={{ top: 16, right: 16, bottom: 10, left: 10 }}>
                    <CartesianGrid stroke="rgba(255,255,255,0.08)" />
                    <XAxis type="number" dataKey="x" name="Inventory bin" tickCount={5} domain={[-0.5, 4.5]} stroke="#94a3b8" />
                    <YAxis type="number" dataKey="y" name="Time remaining bin" tickCount={4} domain={[-0.5, 3.5]} stroke="#94a3b8" strokeWidth={1} />
                    <Tooltip content={<CustomTooltip labelFormatter={(point) => `Inventory ${point.inventory_bin}, Time ${point.time_remaining_bin}`} />} />
                    <Scatter data={valueHeatmap.cells} shape={<SquarePoint />}>
                      {valueHeatmap.cells.map((entry, index) => (
                        <Cell key={`value-cell-${index}`} fill={entry.fill} />
                      ))}
                    </Scatter>
                  </ScatterChart>
                </ResponsiveContainer>
              </div>
              <p className="mt-4 text-sm text-slate-400">{valueCheck?.detail ?? "No sanity-check data available."}</p>
            </article>
          </div>
        </section>

        <section id="adverse-selection" className="mb-14 rounded-[2rem] border border-white/8 bg-slate-900/60 p-6 lg:p-8">
          <SectionHeader
            kicker="Panel 2"
            title="Adverse Selection Sweep"
            copy="This section tracks how spreads, market-maker profitability, and price impact evolve as the informed-trader fraction increases."
          />
          <SourceNotice source={deferredDatasets.adverseSelection.source} message={deferredDatasets.adverseSelection.message} />

          <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard label="Conditions run" value={adverseSelectionSeries.length} />
            <MetricCard label="Total sessions" value={adverseSelectionSeries.reduce((sum, row) => sum + Number(row.sessions ?? 0), 0)} />
            <MetricCard label="Spread range" value={`${spreadRange.min.toFixed(1)} to ${spreadRange.max.toFixed(1)}`} />
            <MetricCard label="P&L range" value={`${pnlRange.min.toFixed(1)} to ${pnlRange.max.toFixed(1)}`} tone={pnlRange.max > 0 ? "text-amber-200" : "text-white"} />
          </div>

          <div className="mt-6 grid gap-6 xl:grid-cols-[1.45fr_0.9fr]">
            <article className="rounded-3xl border border-white/8 bg-slate-950/45 p-5">
              <div className="mb-3 flex items-center justify-between">
                <div>
                  <h3 className="text-lg font-medium text-white">Spread vs Market Maker P&amp;L</h3>
                  <p className="mt-1 text-sm text-slate-400">Spread should rise while P&amp;L compresses as informed flow becomes dominant.</p>
                </div>
                <div className="rounded-full border border-white/10 px-3 py-1 text-xs text-slate-300">
                  {breakevenPoint ? `Breakeven near ${breakevenPoint.informed_fraction_pct}%` : "No breakeven in range"}
                </div>
              </div>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={adverseSelectionSeries} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                    <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                    <XAxis dataKey="informed_fraction_pct" stroke="#94a3b8" unit="%" />
                    <YAxis yAxisId="spread" stroke="#94a3b8" />
                    <YAxis yAxisId="pnl" orientation="right" stroke="#94a3b8" />
                    <Tooltip />
                    <Legend />
                    <Line yAxisId="spread" type="monotone" dataKey="mean_quoted_spread" name="Mean quoted spread" stroke="#7dd3fc" strokeWidth={2.5} dot={{ r: 3 }} />
                    <Line yAxisId="pnl" type="monotone" dataKey="mean_market_maker_pnl" name="MM P&L" stroke="#f59e0b" strokeWidth={2.5} dot={{ r: 3 }} />
                    <Line yAxisId="pnl" type="monotone" dataKey="breakeven_pnl" name="Breakeven" stroke="transparent" dot={{ r: 6, stroke: "#f8fafc", strokeWidth: 2, fill: "#ef4444" }} activeDot={{ r: 6 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </article>

            <article className="rounded-3xl border border-white/8 bg-slate-950/45 p-5">
              <div className="mb-3">
                <h3 className="text-lg font-medium text-white">Kyle&apos;s Lambda</h3>
                <p className="mt-1 text-sm text-slate-400">Price impact should steepen as the informed fraction rises.</p>
              </div>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={adverseSelectionSeries} margin={{ top: 10, right: 20, left: 0, bottom: 10 }}>
                    <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                    <XAxis dataKey="informed_fraction_pct" stroke="#94a3b8" unit="%" />
                    <YAxis stroke="#94a3b8" />
                    <Tooltip />
                    <Line type="monotone" dataKey="mean_kyle_lambda" stroke="#22c55e" strokeWidth={2.5} dot={{ r: 3 }} name="Mean Kyle's λ" />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </article>
          </div>
        </section>

        <section id="pnl-decomposition" className="mb-10 rounded-[2rem] border border-white/8 bg-slate-900/60 p-6 lg:p-8">
          <SectionHeader
            kicker="Panel 3"
            title="P&amp;L Decomposition"
            copy="This final panel breaks profitability into spread capture, adverse-selection drag, and inventory management so the source of returns is visible at a glance."
          />
          <SourceNotice source={deferredDatasets.sessionPnl.source} message={deferredDatasets.sessionPnl.message} />

          <div className="mt-6 grid gap-6 xl:grid-cols-[1.2fr_0.95fr]">
            <article className="rounded-3xl border border-white/8 bg-slate-950/45 p-5">
              <div className="mb-3">
                <h3 className="text-lg font-medium text-white">Attribution Stack</h3>
                <p className="mt-1 text-sm text-slate-400">Green means spread capture. Red shows adverse-selection cost. Blue-gray reflects inventory effects.</p>
              </div>
              <div className="h-80">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={pnlRows} margin={{ top: 10, right: 16, left: 0, bottom: 10 }}>
                    <CartesianGrid stroke="rgba(255,255,255,0.08)" vertical={false} />
                    <XAxis dataKey="agent" stroke="#94a3b8" />
                    <YAxis stroke="#94a3b8" />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="spread_capture" stackId="pnl" fill="#22c55e" name="Spread capture" />
                    <Bar dataKey="adverse_selection_cost" stackId="pnl" fill="#ef4444" name="Adverse selection" />
                    <Bar dataKey="inventory_management" stackId="pnl" fill="#94a3b8" name="Inventory" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </article>

            <article className="rounded-3xl border border-white/8 bg-slate-950/45 p-5">
              <div className="mb-3">
                <h3 className="text-lg font-medium text-white">Summary Table</h3>
                <p className="mt-1 text-sm text-slate-400">The dominant component is highlighted to show whether each agent&apos;s result comes from quoting edge, inventory drift, or information flow.</p>
              </div>
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-white/10 text-sm">
                  <thead>
                    <tr className="text-left text-slate-400">
                      <th className="pb-3 pr-4 font-medium">Agent</th>
                      <th className="pb-3 pr-4 font-medium">Type</th>
                      <th className="pb-3 pr-4 font-medium">Total</th>
                      <th className="pb-3 pr-4 font-medium">Spread</th>
                      <th className="pb-3 pr-4 font-medium">Adverse</th>
                      <th className="pb-3 pr-4 font-medium">Inventory</th>
                      <th className="pb-3 font-medium">Dominant</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/6 text-slate-200">
                    {pnlRows.map((row) => (
                      <tr key={row.agent}>
                        <td className="py-3 pr-4 font-medium text-white">{row.agent}</td>
                        <td className="py-3 pr-4">{row.agent_type}</td>
                        <td className="py-3 pr-4 font-mono">{formatSigned(row.total_pnl)}</td>
                        <td className="py-3 pr-4 font-mono text-emerald-300">{formatSigned(row.spread_capture)}</td>
                        <td className="py-3 pr-4 font-mono text-rose-300">{formatSigned(row.adverse_selection_cost)}</td>
                        <td className="py-3 pr-4 font-mono text-slate-300">{formatSigned(row.inventory_management)}</td>
                        <td className="py-3">
                          <span className="rounded-full border border-sky-500/25 bg-sky-500/10 px-3 py-1 text-xs text-sky-200">{row.dominant_component}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>
  );
}
