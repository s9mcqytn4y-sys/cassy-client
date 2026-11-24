"use client";

import { useLocale } from "@/components/Providers";
import React, { useEffect, useState } from "react";
import { Download } from "lucide-react";
import QRCode from "qrcode";

export interface QrDownloadMeta {
  storeName?: string;
  orderLabel?: string;
  amountLabel?: string;
  deadlineLabel?: string;
  filename?: string;
  pngLabel?: string;
  onDownloaded?: () => void;
}

interface QrCodeSvgProps {
  value: string;
  size?: number;
  label?: string;
  downloadMeta?: QrDownloadMeta;
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function QrCodeSvg({ value, size = 180, label, downloadMeta }: QrCodeSvgProps) {
  const { t } = useLocale();
  const [svgMarkup, setSvgMarkup] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (!value || !value.trim()) {
      setSvgMarkup("");
      return;
    }

    // M, not H: this QR is shown on a screen with nothing over it, and fewer, larger modules scan more
    // reliably from a screenshot uploaded to an e-wallet (W1).
    QRCode.toString(value, {
      type: "svg",
      margin: 2,
      width: size,
      errorCorrectionLevel: "M",
      color: {
        dark: "#17211E",
        light: "#FFFFFF"
      }
    })
      .then((svg) => {
        if (active) {
          setSvgMarkup(svg);
          setError(null);
        }
      })
      .catch(() => {
        if (active) {
          setError("Gagal membentuk SVG QR.");
        }
      });

    return () => {
      active = false;
    };
  }, [value, size]);

  const baseName = (downloadMeta?.filename || "cassy-qris").replace(/[^a-z0-9-_]+/gi, "-").toLowerCase();

  const handleDownloadPng = async () => {
    if (!value.trim() || !downloadMeta) return;
    const width = 720;
    const height = 960;
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    ctx.fillStyle = "#F7F5F0";
    ctx.fillRect(0, 0, width, height);

    // Top Pandan brand header + Kunyit accent stripe
    ctx.fillStyle = "#1F5F52";
    ctx.fillRect(0, 0, width, 168);
    ctx.fillStyle = "#E9A23B";
    ctx.fillRect(0, 168, width, 6);

    ctx.fillStyle = "#E9A23B";
    ctx.beginPath();
    ctx.arc(64, 70, 16, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = "#FFFFFF";
    ctx.font = "800 34px Plus Jakarta Sans, Arial, sans-serif";
    ctx.fillText("Cassy.", 94, 81);

    ctx.fillStyle = "#E7EFEA";
    ctx.font = "600 24px Plus Jakarta Sans, Arial, sans-serif";
    ctx.fillText(downloadMeta.storeName || "Cassy POS", 94, 124);

    // Safe crisp white frame with >= 4 module quiet zone for wallet uploads
    ctx.fillStyle = "#FFFFFF";
    ctx.fillRect(80, 212, 560, 560);
    ctx.strokeStyle = "#E2DFD7";
    ctx.lineWidth = 2;
    ctx.strokeRect(80, 212, 560, 560);

    const qrCanvas = document.createElement("canvas");
    await QRCode.toCanvas(qrCanvas, value, {
      margin: 3,
      width: 520,
      errorCorrectionLevel: "M",
      color: { dark: "#17211E", light: "#FFFFFF" }
    });
    ctx.drawImage(qrCanvas, 100, 232, 520, 520);

    ctx.fillStyle = "#17211E";
    ctx.font = "700 28px Plus Jakarta Sans, Arial, sans-serif";
    ctx.textAlign = "center";
    const sub = [downloadMeta.orderLabel, downloadMeta.amountLabel].filter(Boolean).join(" | ") || "Pembayaran QRIS";
    ctx.fillText(sub, width / 2, 830);

    if (downloadMeta.deadlineLabel) {
      ctx.fillStyle = "#8A5300";
      ctx.font = "700 22px Plus Jakarta Sans, Arial, sans-serif";
      ctx.fillText(downloadMeta.deadlineLabel, width / 2, 872);
    }

    ctx.fillStyle = "#4B5854";
    ctx.font = "500 19px Plus Jakarta Sans, Arial, sans-serif";
    ctx.fillText("QRIS Dinamis oleh Cassy x Midtrans", width / 2, 916);

    canvas.toBlob((blob) => {
      if (!blob) return;
      triggerDownload(blob, `${baseName}.png`);
      downloadMeta.onDownloaded?.();
    }, "image/png");
  };

  if (error) {
    return (
      <div role="alert" className="rounded-xl border border-subtle bg-muted p-3 text-xs text-ink">
        {error}
      </div>
    );
  }

  if (!svgMarkup) {
    return (
      <div
        style={{ width: size, height: size }}
        className="grid place-items-center rounded-xl border border-subtle bg-white p-2 text-xs text-neutral-700"
      >
        {t.qrPreparing}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <div
        role="img"
        aria-label={label ?? t.qrDefaultLabel}
        style={{ width: size, height: size }}
        className="relative inline-block overflow-hidden rounded-xl border border-neutral-300 bg-white p-1.5 shadow-sm"
      >
        <div className="h-full w-full" dangerouslySetInnerHTML={{ __html: svgMarkup }} />
      </div>
      {downloadMeta ? (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={() => void handleDownloadPng()}
            className="btn-secondary inline-flex min-h-[48px] items-center gap-2 px-5 text-sm font-semibold motion-safe:transition-transform motion-safe:active:scale-[0.98]"
          >
            <Download size={16} aria-hidden="true" />
            <span>{downloadMeta.pngLabel ?? "Unduh QRIS"}</span>
          </button>
        </div>
      ) : null}
    </div>
  );
}
