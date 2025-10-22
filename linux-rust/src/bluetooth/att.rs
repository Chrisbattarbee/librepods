use bluer::l2cap::{SocketAddr, Socket, SeqPacket};
use bluer::{Address, AddressType, Result, Error};
use log::{info, error, debug};
use std::sync::Arc;
use tokio::sync::{Mutex, mpsc};
use tokio::task::JoinSet;
use tokio::time::{sleep, Duration, Instant};
use std::collections::HashMap;
use hex;

const PSM_ATT: u16 = 0x001F;
const CONNECT_TIMEOUT: Duration = Duration::from_secs(10);
const POLL_INTERVAL: Duration = Duration::from_millis(200);

const OPCODE_READ_REQUEST: u8 = 0x0A;
const OPCODE_WRITE_REQUEST: u8 = 0x12;
const OPCODE_HANDLE_VALUE_NTF: u8 = 0x1B;
const OPCODE_WRITE_RESPONSE: u8 = 0x13;

#[repr(u16)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ATTHandles {
    Transparency = 0x18,
    LoudSoundReduction = 0x1B,
    HearingAid = 0x2A,
}

#[repr(u16)]
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ATTCCCDHandles {
    Transparency = ATTHandles::Transparency as u16 + 1,
    LoudSoundReduction = ATTHandles::LoudSoundReduction as u16 + 1,
    HearingAid = ATTHandles::HearingAid as u16 + 1,
}

impl From<ATTHandles> for ATTCCCDHandles {
    fn from(handle: ATTHandles) -> Self {
        match handle {
            ATTHandles::Transparency => ATTCCCDHandles::Transparency,
            ATTHandles::LoudSoundReduction => ATTCCCDHandles::LoudSoundReduction,
            ATTHandles::HearingAid => ATTCCCDHandles::HearingAid,
        }
    }
}

struct ATTManagerState {
    sender: Option<mpsc::Sender<Vec<u8>>>,
    listeners: HashMap<u16, Vec<mpsc::UnboundedSender<Vec<u8>>>>,
    responses: mpsc::UnboundedReceiver<Vec<u8>>,
    response_tx: mpsc::UnboundedSender<Vec<u8>>,
}

impl ATTManagerState {
    fn new() -> Self {
        let (tx, rx) = mpsc::unbounded_channel();
        ATTManagerState {
            sender: None,
            listeners: HashMap::new(),
            responses: rx,
            response_tx: tx,
        }
    }
}

#[derive(Clone)]
pub struct ATTManager {
    state: Arc<Mutex<ATTManagerState>>,
    tasks: Arc<Mutex<JoinSet<()>>>,
}

impl ATTManager {
    pub fn new() -> Self {
        ATTManager {
            state: Arc::new(Mutex::new(ATTManagerState::new())),
            tasks: Arc::new(Mutex::new(JoinSet::new())),
        }
    }

    pub async fn connect(&mut self, addr: Address) -> Result<()> {
        info!("ATTManager connecting to {} on PSM {:#06X}...", addr, PSM_ATT);
        let target_sa = SocketAddr::new(addr, AddressType::BrEdr, PSM_ATT);

        let socket = Socket::new_seq_packet()?;
        let seq_packet_result = tokio::time::timeout(CONNECT_TIMEOUT, socket.connect(target_sa)).await;
        let seq_packet = match seq_packet_result {
            Ok(Ok(s)) => Arc::new(s),
            Ok(Err(e)) => {
                error!("L2CAP connect failed: {}", e);
                return Err(e.into());
            }
            Err(_) => {
                error!("L2CAP connect timed out");
                return Err(Error::from(std::io::Error::new(std::io::ErrorKind::TimedOut, "Connection timeout")));
            }
        };

        // Wait for connection to be fully established
        let start = Instant::now();
        loop {
            match seq_packet.peer_addr() {
                Ok(peer) if peer.cid != 0 => break,
                Ok(_) => {}
                Err(e) => {
                    if e.raw_os_error() == Some(107) { // ENOTCONN
                        error!("Peer has disconnected during connection setup.");
                        return Err(e.into());
                    }
                    error!("Error getting peer address: {}", e);
                }
            }
            if start.elapsed() >= CONNECT_TIMEOUT {
                error!("Timed out waiting for L2CAP connection to be fully established.");
                return Err(Error::from(std::io::Error::new(std::io::ErrorKind::TimedOut, "Connection timeout")));
            }
            sleep(POLL_INTERVAL).await;
        }

        info!("L2CAP connection established with {}", addr);

        let (tx, rx) = mpsc::channel(128);
        let state = ATTManagerState::new();
        {
            let mut s = self.state.lock().await;
            *s = state;
            s.sender = Some(tx);
        }

        let manager_clone = self.clone();
        let mut tasks = self.tasks.lock().await;
        tasks.spawn(recv_thread(manager_clone, seq_packet.clone()));
        tasks.spawn(send_thread(rx, seq_packet));

        Ok(())
    }

    pub async fn register_listener(&self, handle: ATTHandles, tx: mpsc::UnboundedSender<Vec<u8>>) {
        let mut state = self.state.lock().await;
        state.listeners.entry(handle as u16).or_default().push(tx);
    }

    pub async fn enable_notifications(&self, handle: ATTHandles) -> Result<()> {
        self.write_cccd(handle.into(), &[0x01, 0x00]).await
    }

    pub async fn read(&self, handle: ATTHandles) -> Result<Vec<u8>> {
        let lsb = (handle as u16 & 0xFF) as u8;
        let msb = ((handle as u16 >> 8) & 0xFF) as u8;
        let pdu = vec![OPCODE_READ_REQUEST, lsb, msb];
        self.send_packet(&pdu).await?;
        self.read_response().await
    }

    pub async fn write(&self, handle: ATTHandles, value: &[u8]) -> Result<()> {
        let lsb = (handle as u16 & 0xFF) as u8;
        let msb = ((handle as u16 >> 8) & 0xFF) as u8;
        let mut pdu = vec![OPCODE_WRITE_REQUEST, lsb, msb];
        pdu.extend_from_slice(value);
        self.send_packet(&pdu).await?;
        self.read_response().await?;
        Ok(())
    }

    async fn write_cccd(&self, handle: ATTCCCDHandles, value: &[u8]) -> Result<()> {
        let lsb = (handle as u16 & 0xFF) as u8;
        let msb = ((handle as u16 >> 8) & 0xFF) as u8;
        let mut pdu = vec![OPCODE_WRITE_REQUEST, lsb, msb];
        pdu.extend_from_slice(value);
        self.send_packet(&pdu).await?;
        self.read_response().await?;
        Ok(())
    }

    async fn send_packet(&self, data: &[u8]) -> Result<()> {
        let state = self.state.lock().await;
        if let Some(sender) = &state.sender {
            sender.send(data.to_vec()).await.map_err(|e| {
                error!("Failed to send packet to channel: {}", e);
                Error::from(std::io::Error::new(std::io::ErrorKind::NotConnected, "L2CAP send channel closed"))
            })
        } else {
            error!("Cannot send packet, sender is not available.");
            Err(Error::from(std::io::Error::new(std::io::ErrorKind::NotConnected, "L2CAP stream not connected")))
        }
    }

    async fn read_response(&self) -> Result<Vec<u8>> {
        let mut state = self.state.lock().await;
        match tokio::time::timeout(Duration::from_millis(2000), state.responses.recv()).await {
            Ok(Some(resp)) => Ok(resp),
            Ok(None) => Err(Error::from(std::io::Error::new(std::io::ErrorKind::UnexpectedEof, "Response channel closed"))),
            Err(_) => Err(Error::from(std::io::Error::new(std::io::ErrorKind::TimedOut, "Response timeout"))),
        }
    }
}

async fn recv_thread(manager: ATTManager, sp: Arc<SeqPacket>) {
    let mut buf = vec![0u8; 1024];
    loop {
        match sp.recv(&mut buf).await {
            Ok(0) => {
                info!("Remote closed the connection.");
                break;
            }
            Ok(n) => {
                let data = &buf[..n];
                debug!("Received {} bytes: {}", n, hex::encode(data));
                if data.is_empty() {
                    continue;
                }
                if data[0] == OPCODE_HANDLE_VALUE_NTF {
                    // Notification
                    let handle = (data[1] as u16) | ((data[2] as u16) << 8);
                    let value = data[3..].to_vec();
                    let state = manager.state.lock().await;
                    if let Some(listeners) = state.listeners.get(&handle) {
                        for listener in listeners {
                            let _ = listener.send(value.clone());
                        }
                    }
                } else {
                    // Response
                    let state = manager.state.lock().await;
                    let _ = state.response_tx.send(data[1..].to_vec());
                }
            }
            Err(e) => {
                error!("read error: {}", e);
                break;
            }
        }
    }
    let mut state = manager.state.lock().await;
    state.sender = None;
}

async fn send_thread(mut rx: mpsc::Receiver<Vec<u8>>, sp: Arc<SeqPacket>) {
    while let Some(data) = rx.recv().await {
        if let Err(e) = sp.send(&data).await {
            error!("Failed to send data: {}", e);
            break;
        }
        debug!("Sent {} bytes: {}", data.len(), hex::encode(&data));
    }
    info!("send thread finished.");
}
