#!/usr/bin/env python3
"""Forward a local TCP port through a specific macOS network interface."""

from __future__ import annotations

import argparse
import asyncio
import socket
import struct


IP_BOUND_IF = 25  # macOS <netinet/in.h>


async def pipe(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
    replacements: tuple[tuple[bytes, bytes], ...] = (),
) -> None:
    try:
        while chunk := await reader.read(65536):
            for source, target in replacements:
                chunk = chunk.replace(source, target)
            writer.write(chunk)
            await writer.drain()
    finally:
        writer.close()


async def handle_client(
    client_reader: asyncio.StreamReader,
    client_writer: asyncio.StreamWriter,
    remote_host: str,
    remote_port: int,
    interface_index: int,
    listen_port: int,
    rewrite_host: str | None,
) -> None:
    loop = asyncio.get_running_loop()
    remote_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    remote_socket.setblocking(False)
    remote_socket.setsockopt(
        socket.IPPROTO_IP, IP_BOUND_IF, struct.pack("I", interface_index)
    )
    try:
        await loop.sock_connect(remote_socket, (remote_host, remote_port))
        remote_reader, remote_writer = await asyncio.open_connection(sock=remote_socket)
        request_replacements: tuple[tuple[bytes, bytes], ...] = ()
        response_replacements: tuple[tuple[bytes, bytes], ...] = ()
        if rewrite_host:
            local_authority = f"127.0.0.1:{listen_port}".encode()
            remote_authority = rewrite_host.encode()
            request_replacements = ((b"Host: " + local_authority, b"Host: " + remote_authority),)
            response_replacements = (
                (b"http://" + remote_authority, b"http://" + local_authority),
            )
        await asyncio.gather(
            pipe(client_reader, remote_writer, request_replacements),
            pipe(remote_reader, client_writer, response_replacements),
        )
    except (ConnectionError, OSError):
        client_writer.close()
    finally:
        await client_writer.wait_closed()


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--listen-port", type=int, required=True)
    parser.add_argument("--remote-host", required=True)
    parser.add_argument("--remote-port", type=int, default=80)
    parser.add_argument("--interface", default="en0")
    parser.add_argument("--rewrite-host")
    args = parser.parse_args()

    interface_index = socket.if_nametoindex(args.interface)
    server = await asyncio.start_server(
        lambda reader, writer: handle_client(
            reader,
            writer,
            args.remote_host,
            args.remote_port,
            interface_index,
            args.listen_port,
            args.rewrite_host,
        ),
        "127.0.0.1",
        args.listen_port,
    )
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
