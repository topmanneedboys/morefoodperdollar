"""Injectable Argentina-local freshness clock."""

from __future__ import annotations

from datetime import date, datetime, timedelta, timezone
from typing import Protocol
from zoneinfo import ZoneInfo


ARGENTINA_ZONE = ZoneInfo("America/Argentina/Buenos_Aires")


class Clock(Protocol):
    def now(self) -> datetime: ...


class SystemClock:
    def now(self) -> datetime:
        return datetime.now(timezone.utc).astimezone(ARGENTINA_ZONE)


class FrozenClock:
    def __init__(self, value: datetime):
        self.value = value if value.tzinfo is not None else value.replace(tzinfo=ARGENTINA_ZONE)

    def now(self) -> datetime:
        return self.value

    def advance(self, **kwargs: int) -> None:
        self.value += timedelta(**kwargs)


def freshness(release_date: str, *, clock: Clock, max_age_days: int = 7) -> str:
    try:
        released = date.fromisoformat(release_date)
    except ValueError as exc:
        raise ValueError("release date is invalid") from exc
    age = (clock.now().astimezone(ARGENTINA_ZONE).date() - released).days
    return "FRESH" if 0 <= age <= max_age_days else "STALE"


__all__ = ["ARGENTINA_ZONE", "Clock", "FrozenClock", "SystemClock", "freshness"]
