"""Portfolio calculations. Pure functions over rows, so they are easy to test."""

import polars as pl

PHASE_ORDER = [
    "INITIATION",
    "PROCUREMENT",
    "PLANNING",
    "EXECUTION",
    "CLOSEOUT",
    "IMPLEMENTATION",
]

SCHEMA = {
    "code": pl.String,
    "phase": pl.String,
    "status": pl.String,
    "budget_minor": pl.Int64,
    "currency": pl.String,
}


def portfolio_summary(rows: list[dict], reporting_currency: str = "KES") -> dict:
    """Counts and budgets of active projects, by lifecycle phase.

    Budgets are summed only for projects in the reporting currency; ``mixed_currency``
    tells the caller that some active projects were left out of the money totals.
    Amounts stay in integer minor units (cents).
    """
    df = pl.DataFrame(rows, schema=SCHEMA) if rows else pl.DataFrame(schema=SCHEMA)
    active = df.filter(pl.col("status") == "ACTIVE")
    in_currency = active.filter(pl.col("currency") == reporting_currency)

    counts = active.group_by("phase").agg(pl.len().alias("count"))
    budgets = in_currency.group_by("phase").agg(pl.col("budget_minor").sum().alias("budget_minor"))
    by_phase = {
        r["phase"]: r
        for r in counts.join(budgets, on="phase", how="left")
        .with_columns(pl.col("budget_minor").fill_null(0))
        .to_dicts()
    }

    return {
        "currency": reporting_currency,
        "active_count": active.height,
        "total_budget_minor": int(in_currency["budget_minor"].sum() or 0),
        "mixed_currency": in_currency.height < active.height,
        "by_phase": [
            {
                "phase": phase,
                "count": int(by_phase[phase]["count"]),
                "budget_minor": int(by_phase[phase]["budget_minor"]),
            }
            for phase in PHASE_ORDER
            if phase in by_phase
        ],
    }
