from fastapi import FastAPI, Query
import requests
import re
import json
from datetime import datetime
from cachetools import TTLCache
import time

app = FastAPI(title="Stock Data Service")

# 本地缓存：最多缓存 200 条，每条存活 30 秒
cache = TTLCache(maxsize=200, ttl=30)

def get_with_retry(url, retries=2, delay=1):
    """带重试的 HTTP GET"""
    for i in range(retries + 1):
        try:
            resp = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, timeout=5)
            resp.encoding = "gb2312"
            return resp.text
        except Exception as e:
            if i == retries:
                raise e
            time.sleep(delay)

def sina_realtime(symbol: str):
    """新浪财经实时行情（A股）"""
    # 确定前缀
    if symbol.startswith("6"):
        code = f"sh{symbol}"
    else:
        code = f"sz{symbol}"
    url = f"http://hq.sinajs.cn/list={code}"
    text = get_with_retry(url)
    if not text or "FAILED" in text:
        return None

    # 解析：格式 var hq_str_sh600519="名称,今开,昨收,当前价,最高,最低,成交量(手),成交额(万)..."
    try:
        arr = text.split('"')[1].split(',')
        return {
            "symbol": symbol,
            "name": arr[0],
            "price": float(arr[3]),
            "change_pct": round((float(arr[3]) - float(arr[2])) / float(arr[2]) * 100, 2),
            "volume": int(arr[8]),          # 成交量（手）
            "high": float(arr[4]),
            "low": float(arr[5]),
            "time": datetime.now().isoformat()
        }
    except Exception:
        return None

def sina_history(symbol: str, count: int = 100):
    """新浪历史日K线（最近 count 条）"""
    if symbol.startswith("6"):
        code = f"sh{symbol}"
    else:
        code = f"sz{symbol}"
    url = f"http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData?symbol={code}&scale=240&ma=no&datalen={count}"
    try:
        resp = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, timeout=10)
        resp.encoding = "gb2312"
        data = resp.json()
        result = []
        for item in data:
            result.append({
                "date": item["day"],
                "open": float(item["open"]),
                "high": float(item["high"]),
                "low": float(item["low"]),
                "close": float(item["close"]),
                "volume": float(item["volume"])
            })
        return result
    except Exception as e:
        return {"error": str(e)}

@app.get("/stock/realtime")
async def get_realtime(symbol: str = Query(..., description="股票代码")):
    cache_key = f"realtime:{symbol}"
    if cache_key in cache:
        return cache[cache_key]
    data = sina_realtime(symbol)
    if not data:
        return {"error": "获取失败", "symbol": symbol}
    cache[cache_key] = data
    return data

@app.get("/stock/history")
async def get_history(
        symbol: str = Query(...),
        count: int = Query(100)
):
    cache_key = f"history:{symbol}:{count}"
    if cache_key in cache:
        return cache[cache_key]
    result = sina_history(symbol, count)
    if isinstance(result, dict) and "error" in result:
        return result
    data = {"symbol": symbol, "data": result}
    cache[cache_key] = data
    return data

@app.get("/health")
async def health():
    return {"status": "ok"}