import asyncio
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app import main


class KafkaConsumerOffsetTest(unittest.IsolatedAsyncioTestCase):
    async def test_new_group_consumes_event_already_available_from_earliest(self) -> None:
        event = {"event_id": "match-1:1", "event_type": "MATCH_CREATED", "match_id": "match-1"}
        created_with: dict[str, object] = {}
        stop_event = asyncio.Event()
        main.app.state.stop_event = stop_event

        class FakeConsumer:
            def __init__(self, topic: str, **kwargs: object) -> None:
                created_with.update({"topic": topic, **kwargs})
                self.delivered = False

            async def start(self) -> None:
                return None

            def __aiter__(self):
                return self

            async def __anext__(self):
                if not self.delivered:
                    self.delivered = True
                    return SimpleNamespace(value=event)
                stop_event.set()
                raise StopAsyncIteration

            async def commit(self) -> None:
                return None

            async def stop(self) -> None:
                return None

        with patch.object(main, "AIOKafkaConsumer", FakeConsumer), patch.object(main, "handle_event") as handle_event:
            await main.consume_topic("match-events", "fresh-group")

        self.assertEqual("earliest", created_with["auto_offset_reset"])
        handle_event.assert_called_once_with("match-events", event)


if __name__ == "__main__":
    unittest.main()
